package com.jimbroze.kbus.generation.processors

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.jimbroze.kbus.annotations.KbusIndex
import com.jimbroze.kbus.annotations.LoadMessageHandler
import com.jimbroze.kbus.generation.generators.AutoLoaderGenerator
import com.jimbroze.kbus.generation.generators.BusGenerator
import com.jimbroze.kbus.generation.generators.ContainerInterfaceGenerator
import com.jimbroze.kbus.generation.generators.DependencyIndexGenerator
import com.jimbroze.kbus.generation.generators.HandlersFactoryGenerator
import com.jimbroze.kbus.generation.generators.HandlersInterfaceGenerator
import com.jimbroze.kbus.generation.processing.IndexParser
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependencyProperties
import com.jimbroze.kbus.generation.processing.handlers.HandlerFactory
import com.jimbroze.kbus.generation.processors.context.HandlersAndDependencies
import com.jimbroze.kbus.generation.processors.visitors.DependencyIndexVisitor
import com.jimbroze.kbus.generation.processors.visitors.LoadVisitor

class CodeGenerators(
    val containerInterface: ContainerInterfaceGenerator,
    val handlersInterface: HandlersInterfaceGenerator,
    val autoLoader: AutoLoaderGenerator,
    val handlersFactory: HandlersFactoryGenerator,
    val dependencyIndexGenerator: DependencyIndexGenerator,
    val bus: BusGenerator,
)

class KbusProcessor(
    @Suppress("unused") private val logger: KSPLogger,
    private val handlerFactory: HandlerFactory,
    private val indexParser: IndexParser,
    private val generators: CodeGenerators,
    private val isSubModule: Boolean,
    private val indexPackagePath: String,
) : SymbolProcessor {
    private val dependencies = HandlersAndDependencies()

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val invalidSymbols = mutableListOf<KSAnnotated>()

        if (!isSubModule) {
            invalidSymbols.addAll(processIndexes(resolver))
        }

        invalidSymbols.addAll(
            processMessages(resolver, CommandDependencyProperties.fromResolver(resolver))
        )

        return invalidSymbols
    }

    @OptIn(KspExperimental::class)
    private fun processIndexes(resolver: Resolver): List<KSAnnotated> {
        val localIndexes =
            resolver.getSymbolsWithAnnotation(KbusIndex::class.qualifiedName.toString())
        val libraryIndexes =
            resolver
                .getDeclarationsFromPackage(indexPackagePath)
                .filterIsInstance<KSClassDeclaration>()
                .filter { classDecl ->
                    classDecl.annotations.any {
                        it.shortName.asString() == KbusIndex::class.simpleName &&
                            it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                                KbusIndex::class.qualifiedName
                    }
                }
        val dependencyIndexes = localIndexes + libraryIndexes
        val (validIndexSymbols, invalidIndexSymbols) = dependencyIndexes.partition { it.validate() }
        validIndexSymbols.forEach {
            it.accept(DependencyIndexVisitor(indexParser, logger), dependencies)
        }

        return invalidIndexSymbols
    }

    private fun processMessages(
        resolver: Resolver,
        commandDependenciesProps: CommandDependencyProperties,
    ): List<KSAnnotated> {
        val messagesToLoad =
            resolver.getSymbolsWithAnnotation(LoadMessageHandler::class.qualifiedName.toString())

        val (validLoadSymbols, invalidLoadSymbols) = messagesToLoad.partition { it.validate() }
        validLoadSymbols.forEach {
            it.accept(LoadVisitor(commandDependenciesProps, handlerFactory, logger), dependencies)
        }

        return invalidLoadSymbols
    }

    override fun finish() {
        if (dependencies.isEmpty()) return

        generators.containerInterface.generateInterface(dependencies.allDependencies)
        generators.handlersInterface.generateInterface(dependencies.handlers)
        if (isSubModule) {
            generators.dependencyIndexGenerator.generateIndexClass(
                dependencies.allDependencies,
                dependencies.handlers,
            )
        } else {
            generators.autoLoader.generateAutoloader(dependencies.allDependencies)
            generators.handlersFactory.generateClass(dependencies.handlers)
            generators.bus.generateClass(dependencies.handlers)
        }
    }
}
