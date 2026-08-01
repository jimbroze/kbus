package com.jimbroze.kbus.generation.processors

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.jimbroze.kbus.contracts.annotations.LoadEvent
import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.annotations.index.KbusIndex
import com.jimbroze.kbus.generation.generators.AutoLoaderGenerator
import com.jimbroze.kbus.generation.generators.AutoPublishRegistrationsGenerator
import com.jimbroze.kbus.generation.generators.BusGenerator
import com.jimbroze.kbus.generation.generators.ContainerInterfaceGenerator
import com.jimbroze.kbus.generation.generators.DependencyIndexGenerator
import com.jimbroze.kbus.generation.generators.HandlersFactoryGenerator
import com.jimbroze.kbus.generation.generators.HandlersInterfaceGenerator
import com.jimbroze.kbus.generation.generators.LoadedEventHandlersGenerator
import com.jimbroze.kbus.generation.processing.IndexParser
import com.jimbroze.kbus.generation.processing.autopublish.AutoPublishFactory
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependencyProperties
import com.jimbroze.kbus.generation.processing.handlers.HandlerFactory
import com.jimbroze.kbus.generation.processors.context.ProcessingContext
import com.jimbroze.kbus.generation.processors.visitors.DependencyIndexVisitor
import com.jimbroze.kbus.generation.processors.visitors.LoadEventVisitor
import com.jimbroze.kbus.generation.processors.visitors.LoadVisitor

@Suppress("LongParameterList")
class CodeGenerators(
    val containerInterface: ContainerInterfaceGenerator,
    val handlersInterface: HandlersInterfaceGenerator,
    val autoLoader: AutoLoaderGenerator,
    val handlersFactory: HandlersFactoryGenerator,
    val dependencyIndexGenerator: DependencyIndexGenerator,
    val bus: BusGenerator,
    val loadedEventHandlersGenerator: LoadedEventHandlersGenerator,
    val autoPublishRegistrationsGenerator: AutoPublishRegistrationsGenerator,
)

@Suppress("LongParameterList")
class KbusProcessor(
    @Suppress("unused") private val logger: KSPLogger,
    private val handlerFactory: HandlerFactory,
    private val indexParser: IndexParser,
    private val autoPublishFactory: AutoPublishFactory,
    private val generators: CodeGenerators,
    private val isSubModule: Boolean,
    private val indexPackagePath: String,
) : SymbolProcessor {
    private val dependencies = ProcessingContext()

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val invalidSymbols = mutableListOf<KSAnnotated>()

        invalidSymbols.addAll(processIndexes(resolver))

        invalidSymbols.addAll(
            processMessages(resolver, CommandDependencyProperties.fromResolver(resolver))
        )

        invalidSymbols.addAll(processEvents(resolver))

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

    private fun processEvents(resolver: Resolver): List<KSAnnotated> {
        val eventsToLoad =
            resolver.getSymbolsWithAnnotation(LoadEvent::class.qualifiedName.toString())

        val (validEventSymbols, invalidEventSymbols) = eventsToLoad.partition { it.validate() }
        validEventSymbols.forEach {
            it.accept(LoadEventVisitor(autoPublishFactory, logger), dependencies)
        }

        return invalidEventSymbols
    }

    override fun finish() {
        if (dependencies.isEmpty()) return

        val sourceFiles = dependencies.sourceFiles.toList()

        if (isSubModule) {
            // A submodule generates against what it declares. What it learned from its
            // dependencies' indexes is there to generate *with*, not to re-export.
            generators.containerInterface.generateInterface(
                dependencies.locallyDeclaredDependencies,
                sourceFiles,
            )
            generators.handlersInterface.generateInterfaces(
                dependencies.locallyDeclaredHandlers,
                sourceFiles,
            )
            generators.dependencyIndexGenerator.generateIndexClass(
                dependencies.locallyDeclaredDependencies,
                dependencies.locallyDeclaredHandlers,
                dependencies.locallyDeclaredAutoPublishDefinitions,
                sourceFiles,
            )
        } else {
            generators.containerInterface.generateInterface(
                dependencies.allDependencies,
                sourceFiles,
            )
            generators.handlersInterface.generateInterfaces(dependencies.handlers, sourceFiles)
            generators.autoLoader.generateAutoloader(dependencies.allDependencies, sourceFiles)
            generators.handlersFactory.generateClasses(dependencies.handlers, sourceFiles)
            generators.loadedEventHandlersGenerator.generateExtensionProperties(
                dependencies.handlers,
                sourceFiles,
            )
            generators.bus.generateClass(dependencies.handlers, sourceFiles)
            generators.autoPublishRegistrationsGenerator.generateRegistrations(
                dependencies.autoPublishDefinitions,
                sourceFiles,
            )
        }
    }
}
