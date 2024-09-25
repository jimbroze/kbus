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
import com.jimbroze.kbus.core.Command
import com.jimbroze.kbus.core.Message
import com.jimbroze.kbus.core.Query
import com.jimbroze.kbus.generation.DependencyLoaderGenerator.addMethodToBusClass
import com.jimbroze.kbus.generation.DependencyLoaderGenerator.addMethodToLoaderClass
import com.jimbroze.kbus.generation.DependencyLoaderGenerator.generateDependencyLoader
import kbus.annotations.Load
import kotlin.reflect.KClass

private val loadableMessages = listOf(Command::class, Query::class)

data class LoadedHandlerDefinition(
    val handlerDefinition: HandlerDefinition,
    val loadedMessageName: String,
)

data class HandlerDefinition(
    val handler: KSClassDeclaration,
    val message: KSClassDeclaration,
    val messageBaseClass: KClass<out Message>,
)

data class LoadedMessageCode(
    val busMethods: StringBuilder = StringBuilder(),
    val loaderMethods: StringBuilder = StringBuilder(),
    val handlerDependencies: MutableSet<ParameterDefinition> = mutableSetOf(),
) {
    fun addMessage(message: LoadedMessageCode) {
        busMethods.append(message.busMethods)
        loaderMethods.append(message.loaderMethods)
        handlerDependencies.addAll(message.handlerDependencies)
    }
}

class MessageProcessor(private val codeGenerator: CodeGenerator, private val logger: KSPLogger) :
    SymbolProcessor {
    private val loadedMessageGenerator =
        LoadedMessageGenerator(codeGenerator, logger, loadableMessages)

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols =
            resolver
                .getSymbolsWithAnnotation(Load::class.qualifiedName.toString())
                .filterIsInstance<KSClassDeclaration>()

        if (!symbols.iterator().hasNext()) return emptyList()

        val loadedMessages = LoadedMessageCode()

        for (symbol in symbols) {
            symbol.accept(MessageClassVisitor(), Unit)?.let { loadedMessages.addMessage(it) }
        }

        generateDependencyLoader(codeGenerator, loadedMessages)

        val messagesThatCouldNotBeProcessed = symbols.filterNot { it.validate() }.toList()
        return messagesThatCouldNotBeProcessed
    }

    inner class MessageClassVisitor : KSDefaultVisitor<Unit, LoadedMessageCode?>() {
        override fun defaultHandler(node: KSNode, data: Unit): LoadedMessageCode? {
            return null
        }

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration,
            data: Unit,
        ): LoadedMessageCode? {
            if (classDeclaration.classKind != ClassKind.CLASS) {
                logger.error(
                    "Only classes can be annotated with @${Load::class.simpleName}",
                    classDeclaration,
                )
                return null
            }

            return visitMessageHandler(classDeclaration)
        }

        private fun visitMessageHandler(classDeclaration: KSClassDeclaration): LoadedMessageCode? {
            val loadedMessageDefinition =
                loadedMessageGenerator.generateLoadedMessage(classDeclaration) ?: return null

            val handlerDependencies =
                loadedMessageDefinition.handlerDefinition.handler.primaryConstructor!!
                    .parameters
                    .map { getParamNames(it) }
                    .toMutableSet()

            return LoadedMessageCode(
                addMethodToBusClass(loadedMessageDefinition),
                addMethodToLoaderClass(loadedMessageDefinition),
                handlerDependencies,
            )
        }
    }
}
