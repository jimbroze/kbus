package com.jimbroze.kbus.generation.processors

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.validate
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.jimbroze.kbus.annotations.Load
import com.jimbroze.kbus.generation.generators.ContainerInterfaceGenerator
import com.jimbroze.kbus.generation.generators.HandlersInterfaceGenerator
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependencyProperties
import com.jimbroze.kbus.generation.processing.handlers.HandlerFactory
import com.jimbroze.kbus.generation.processors.visitors.HandlersAndDependencies

// FIXME go through TODOs

class MessageProcessor(
    @Suppress("unused") private val logger: KSPLogger,
    private val handlerFactory: HandlerFactory,
    private val containerInterfaceGenerator: ContainerInterfaceGenerator,
    private val handlersInterfaceGenerator: HandlersInterfaceGenerator,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val messagesToLoad = resolver.getSymbolsWithAnnotation(Load::class.qualifiedName.toString())

        processMessagesToLoad(messagesToLoad, CommandDependencyProperties.fromResolver(resolver))

        return messagesToLoad.filterNot { it.validate() }.toList()
    }

    private fun processMessagesToLoad(
        symbols: Sequence<KSAnnotated>,
        commandDependenciesProps: CommandDependencyProperties,
    ) {
        val handlers = HandlersAndDependencies()

        symbols.forEach { it.accept(LoadVisitor(commandDependenciesProps), handlers) }

        if (handlers.isEmpty()) return

        val generatedPackagePath = "com.jimbroze.kbus.generated"

        containerInterfaceGenerator.generateInterface(
            generatedPackagePath,
            handlers.allDependencies,
        )
        handlersInterfaceGenerator.generateInterface(generatedPackagePath, handlers.handlers)
    }

    inner class LoadVisitor(val commandDependenciesProps: CommandDependencyProperties) :
        KSDefaultVisitor<HandlersAndDependencies, Unit>() {

        override fun defaultHandler(node: KSNode, data: HandlersAndDependencies) {
            error(
                "Only classes can be annotated with @${Load::class.simpleName}. " +
                    "$node is not a class"
            )
        }

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration,
            data: HandlersAndDependencies,
        ) {
            if (classDeclaration.classKind != ClassKind.CLASS) {
                error(
                    "Only classes can be annotated with @${Load::class.simpleName}. " +
                        "$classDeclaration is a ${classDeclaration.classKind}"
                )
            }

            // TODO don't like that this modifies data. Move to visitor context!!?
            data.addHandler(classDeclaration, commandDependenciesProps, handlerFactory)
        }
    }
}
