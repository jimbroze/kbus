package com.jimbroze.kbus.generation

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.validate
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.jimbroze.kbus.annotations.GenerateContainer
import com.jimbroze.kbus.annotations.Load
import com.jimbroze.kbus.core.Command
import com.jimbroze.kbus.core.MessageBus
import com.jimbroze.kbus.core.Query
import com.jimbroze.kbus.generation.DependencyLoaderGenerator.Companion.LOADER_INTERFACE_NAME

private val loadableMessages = listOf(Command::class, Query::class)

class MessageProcessor(codeGenerator: CodeGenerator, private val logger: KSPLogger) :
    SymbolProcessor {
    private val busPackageName =
        MessageBus::class.qualifiedName!!.split(".").dropLast(1).joinToString(".")

    private val loadedMessageGenerator =
        LoadedMessageGenerator(codeGenerator, logger, loadableMessages)
    private val dependencyLoaderGenerator =
        DependencyLoaderGenerator(codeGenerator, logger, busPackageName)
    private val busGenerator =
        MessageBusGenerator(codeGenerator, logger, busPackageName, LOADER_INTERFACE_NAME)

    private val dependencyProcessor = DependencyProcessor(busPackageName, logger)

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val messagesThatCouldNotBeProcessed = mutableListOf<KSClassDeclaration>()

        val messagesToLoad =
            resolver
                .getSymbolsWithAnnotation(Load::class.qualifiedName.toString())
                .filterIsInstance<KSClassDeclaration>()

        processMessagesToLoad(messagesToLoad)
        messagesThatCouldNotBeProcessed.addAll(messagesToLoad.filterNot { it.validate() })

        val containerInterfaces =
            resolver
                .getSymbolsWithAnnotation(GenerateContainer::class.qualifiedName.toString())
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.classKind === ClassKind.INTERFACE }

        processContainerInterfaces(containerInterfaces)
        messagesThatCouldNotBeProcessed.addAll(containerInterfaces.filterNot { it.validate() })

        return messagesThatCouldNotBeProcessed.toList()
    }

    private fun processMessagesToLoad(symbols: Sequence<KSClassDeclaration>) {
        val dependencies = mutableSetOf<LoaderDependency>()

        for (symbol in symbols) {
            symbol.accept(MessageDependencyVisitor(), Unit).let { dependencies.addAll(it) }
        }

        if (dependencies.isEmpty()) return

        dependencyLoaderGenerator.generateLoaderInterface(dependencies)
    }

    private fun processContainerInterfaces(symbols: Sequence<KSClassDeclaration>) {
        val dependencies = mutableSetOf<LoaderDependency>()

        for (symbol in symbols) {
            dependencies.addAll(
                dependencyProcessor.generateFrom(symbol.getAllProperties(), includeNested = false)
            )
        }

        val loadedMessages = mutableSetOf<LoadedHandlerDefinition>()
        for (dependency in dependencies) {
            val declaration = dependency.definition.declaration

            if (
                declaration is KSClassDeclaration &&
                    Load::class.qualifiedName in
                        declaration.annotations.map {
                            it.annotationType.resolve().declaration.qualifiedName?.asString()
                        }
            ) {
                declaration.accept(MessageLoaderVisitor(), Unit)?.let { loadedMessages.add(it) }
            }
        }

        if (dependencies.isEmpty()) return

        dependencyLoaderGenerator.generateLoaderClass(dependencies)

        busGenerator.generate(loadedMessages)
    }

    inner class MessageDependencyVisitor : KSDefaultVisitor<Unit, Set<LoaderDependency>>() {
        override fun defaultHandler(node: KSNode, data: Unit): Set<LoaderDependency> {
            return emptySet()
        }

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration,
            data: Unit,
        ): Set<LoaderDependency> {
            if (classDeclaration.classKind != ClassKind.CLASS) {
                logger.error(
                    "Only classes can be annotated with @${Load::class.simpleName}",
                    classDeclaration,
                )
                return emptySet()
            }

            return visitMessageHandler(classDeclaration)
        }

        private fun visitMessageHandler(messageHandler: KSClassDeclaration): Set<LoaderDependency> {
            return dependencyProcessor.generateFrom(
                messageHandler.asStarProjectedType(),
                includeNested = true,
            )
        }
    }

    inner class MessageLoaderVisitor : KSDefaultVisitor<Unit, LoadedHandlerDefinition?>() {
        override fun defaultHandler(node: KSNode, data: Unit): LoadedHandlerDefinition? {
            return null
        }

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration,
            data: Unit,
        ): LoadedHandlerDefinition? {
            if (classDeclaration.classKind != ClassKind.CLASS) {
                logger.error(
                    "Only classes can be annotated with @${Load::class.simpleName}",
                    classDeclaration,
                )
                return null
            }

            return visitMessageHandler(classDeclaration)
        }

        private fun visitMessageHandler(
            classDeclaration: KSClassDeclaration
        ): LoadedHandlerDefinition? {
            return loadedMessageGenerator.generateLoadedMessage(classDeclaration)
        }
    }
}
