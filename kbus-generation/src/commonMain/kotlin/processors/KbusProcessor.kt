package com.jimbroze.kbus.generation.processors

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.jimbroze.kbus.api.annotations.LoadEventMapper
import com.jimbroze.kbus.api.annotations.LoadMessageHandler
import com.jimbroze.kbus.api.annotations.index.KbusIndex
import com.jimbroze.kbus.generation.generators.AutoLoaderGenerator
import com.jimbroze.kbus.generation.generators.AutoPublishRegistrationsGenerator
import com.jimbroze.kbus.generation.generators.BusGenerator
import com.jimbroze.kbus.generation.generators.CommandGatewayGenerator
import com.jimbroze.kbus.generation.generators.ContainerInterfaceGenerator
import com.jimbroze.kbus.generation.generators.ContextCommandsGenerator
import com.jimbroze.kbus.generation.generators.DependencyIndexGenerator
import com.jimbroze.kbus.generation.generators.HandlersFactoryGenerator
import com.jimbroze.kbus.generation.generators.HandlersInterfaceGenerator
import com.jimbroze.kbus.generation.generators.LoadedEventHandlersGenerator
import com.jimbroze.kbus.generation.generators.reportContextIdentityCollisions
import com.jimbroze.kbus.generation.processing.IndexParser
import com.jimbroze.kbus.generation.processing.autopublish.AutoPublishFactory
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependencyProperties
import com.jimbroze.kbus.generation.processing.handlers.HandlerFactory
import com.jimbroze.kbus.generation.processors.context.ProcessingContext
import com.jimbroze.kbus.generation.processors.visitors.DependencyIndexVisitor
import com.jimbroze.kbus.generation.processors.visitors.LoadEventMapperVisitor
import com.jimbroze.kbus.generation.processors.visitors.LoadVisitor
import com.squareup.kotlinpoet.ClassName

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
    val contextCommands: ContextCommandsGenerator,
    val commandGateways: CommandGatewayGenerator,
)

@Suppress("LongParameterList")
class KbusProcessor(
    private val logger: KSPLogger,
    private val handlerFactory: HandlerFactory,
    private val indexParser: IndexParser,
    private val autoPublishFactory: AutoPublishFactory,
    private val generators: CodeGenerators,
    private val isSubModule: Boolean,
    private val indexPackagePath: String,
) : SymbolProcessor {
    private val dependencies = ProcessingContext()
    private var contextCommandInterfaces: Map<String, ClassName>? = null

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val invalidSymbols = mutableListOf<KSAnnotated>()

        invalidSymbols.addAll(processIndexes(resolver))

        generateContextCommandInterfaces(resolver)

        invalidSymbols.addAll(
            processMessages(resolver, CommandDependencyProperties.fromResolver(resolver))
        )

        invalidSymbols.addAll(processEventMappers(resolver))

        return invalidSymbols
    }

    /**
     * A handler naming its own context's commands cannot be resolved until that interface exists,
     * so the interfaces are written before any handler is read. Only the base class a command
     * handler extends is needed for this, which resolves whatever its constructor names.
     */
    private fun generateContextCommandInterfaces(resolver: Resolver) {
        if (contextCommandInterfaces != null) return

        val annotatedHandlers =
            resolver
                .getSymbolsWithAnnotation(LoadMessageHandler::class.qualifiedName.toString())
                .filterIsInstance<KSClassDeclaration>()
                .toList()

        val locallyDeclaredCommands =
            annotatedHandlers
                .mapNotNull { handlerFactory.createCommandHandlerSignature(it) }
                .toSet()

        val sourceFiles = annotatedHandlers.mapNotNull { it.containingFile }.distinct()

        contextCommandInterfaces =
            generators.contextCommands.generateInterfaces(
                locallyDeclaredCommands + dependencies.handlers,
                sourceFiles,
            )
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

    private fun processEventMappers(resolver: Resolver): List<KSAnnotated> {
        val mappersToLoad =
            resolver.getSymbolsWithAnnotation(LoadEventMapper::class.qualifiedName.toString())

        val (validMapperSymbols, invalidMapperSymbols) = mappersToLoad.partition { it.validate() }
        validMapperSymbols.forEach {
            it.accept(LoadEventMapperVisitor(autoPublishFactory, logger), dependencies)
        }

        return invalidMapperSymbols
    }

    override fun finish() {
        if (dependencies.isEmpty()) return
        if (!reportContextIdentityCollisions(dependencies.handlers, logger)) return

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
                contextCommandInterfaces.orEmpty(),
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
            generators.contextCommands.generateExecutors(
                dependencies.handlers,
                dependencies.contextCommandInterfacesFromIndexes.mapValues { (_, interfaces) ->
                    interfaces.toList()
                },
                sourceFiles,
            )
            generators.commandGateways.generateGateways(dependencies.handlers, sourceFiles)
            generators.bus.generateClass(dependencies.handlers, sourceFiles)
            generators.autoPublishRegistrationsGenerator.generateRegistrations(
                dependencies.autoPublishDefinitions,
                sourceFiles,
            )
        }
    }
}
