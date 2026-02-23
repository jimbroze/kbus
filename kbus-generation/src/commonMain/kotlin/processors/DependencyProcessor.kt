package com.jimbroze.kbus.generation.processors

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.validate
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.jimbroze.kbus.annotations.ContainerInterface
import com.jimbroze.kbus.annotations.HandlersInterface
import com.jimbroze.kbus.annotations.KbusIndex
import com.jimbroze.kbus.annotations.LoadMessageHandler
import com.jimbroze.kbus.generation.generators.AutoLoaderGenerator
import com.jimbroze.kbus.generation.generators.BusGenerator
import com.jimbroze.kbus.generation.generators.ContainerInterfaceGenerator
import com.jimbroze.kbus.generation.generators.DependencyIndexGenerator
import com.jimbroze.kbus.generation.generators.HandlersFactoryGenerator
import com.jimbroze.kbus.generation.generators.HandlersInterfaceGenerator
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependencyProperties
import com.jimbroze.kbus.generation.processing.dependencies.DependencyIndexFactory
import com.jimbroze.kbus.generation.processing.dependencies.DependencyOverrideType
import com.jimbroze.kbus.generation.processing.handlers.HandlerFactory
import com.jimbroze.kbus.generation.processors.visitors.HandlersAndDependencies
import kotlin.sequences.forEach

class ContainerGenerators(
    val containerInterface: ContainerInterfaceGenerator,
    val handlersInterface: HandlersInterfaceGenerator,
    val autoLoader: AutoLoaderGenerator,
    val handlersFactory: HandlersFactoryGenerator,
    val dependencyIndexGenerator: DependencyIndexGenerator,
    val bus: BusGenerator,
)

class DependencyProcessor(
    @Suppress("unused") private val logger: KSPLogger,
    private val handlerFactory: HandlerFactory,
    private val dependencyIndexFactory: DependencyIndexFactory,
    private val generators: ContainerGenerators,
    private val shouldGenerateBus: Boolean,
    private val indexPackagePath: String,
) : SymbolProcessor {
    private val dependencies = HandlersAndDependencies()
    private val handlersInterfaces = mutableSetOf<KSClassDeclaration>()
    private val containerInterfaces = mutableSetOf<KSClassDeclaration>()

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val commandDependenciesProps = CommandDependencyProperties.fromResolver(resolver)

        val localIndexes =
            resolver.getSymbolsWithAnnotation(KbusIndex::class.qualifiedName.toString())
        val libraryIndexes =
            resolver
                .getDeclarationsFromPackage(indexPackagePath)
                .filterIsInstance<KSClassDeclaration>()
                .filter { classDecl ->
                    classDecl.annotations.any {
                        it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                            KbusIndex::class.qualifiedName
                    }
                }
        val dependencyIndexes = localIndexes + libraryIndexes
        val (validIndexSymbols, invalidIndexSymbols) = dependencyIndexes.partition { it.validate() }
        validIndexSymbols.forEach { it.accept(DependencyIndexVisitor(), dependencies) }

        val containerInterfaces =
            resolver.getSymbolsWithAnnotation(ContainerInterface::class.qualifiedName.toString())
        val (validContainerSymbols, invalidContainerSymbols) =
            containerInterfaces.partition { it.validate() }
        validContainerSymbols.forEach {
            it.accept(ContainerInterfaceVisitor(commandDependenciesProps), dependencies)
        }

        val handlerInterfaces =
            resolver.getSymbolsWithAnnotation(HandlersInterface::class.qualifiedName.toString())
        val (validHandlerSymbols, invalidHandlerSymbols) =
            handlerInterfaces.partition { it.validate() }
        validHandlerSymbols.forEach {
            it.accept(HandlersInterfaceVisitor(commandDependenciesProps), dependencies)
        }

        val invalidMessageSymbols = processMessages(resolver)

        return invalidIndexSymbols +
            invalidContainerSymbols +
            invalidHandlerSymbols +
            invalidMessageSymbols
    }

    private fun processMessages(resolver: Resolver): List<KSAnnotated> {
        val messagesToLoad =
            resolver.getSymbolsWithAnnotation(LoadMessageHandler::class.qualifiedName.toString())

        messagesToLoad.forEach {
            it.accept(LoadVisitor(CommandDependencyProperties.fromResolver(resolver)), dependencies)
        }

        return messagesToLoad.filterNot { it.validate() }.toList()
    }

    /**
     * Use finish function as this 'consumes' interfaces produced by other processors. Ensure that
     * other processors have run before this
     */
    override fun finish() {
        if (dependencies.isEmpty()) return

        // TODO only put local deps in index?
        generators.containerInterface.generateInterface(dependencies.allDependencies)
        generators.handlersInterface.generateInterface(dependencies.handlers)
        if (shouldGenerateBus) {
            generators.autoLoader.generateAutoloader(dependencies.allDependencies)
            generators.handlersFactory.generateClass(dependencies.handlers)
            generators.bus.generateClass(dependencies.handlers)
        } else {
            generators.dependencyIndexGenerator.generateIndexClass(
                dependencies.allDependencies,
                dependencies.handlers,
            )
        }
    }

    inner class LoadVisitor(val commandDependenciesProps: CommandDependencyProperties) :
        KSDefaultVisitor<HandlersAndDependencies, Unit>() {

        override fun defaultHandler(node: KSNode, data: HandlersAndDependencies) {
            error(
                "Only classes can be annotated with @${LoadMessageHandler::class.simpleName}. " +
                    "$node is not a class"
            )
        }

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration,
            data: HandlersAndDependencies,
        ) {
            if (classDeclaration.classKind != ClassKind.CLASS) {
                error(
                    "Only classes can be annotated with @${LoadMessageHandler::class.simpleName}. " +
                        "$classDeclaration is a ${classDeclaration.classKind}"
                )
            }

            data.addHandler(classDeclaration, commandDependenciesProps, handlerFactory, logger)
        }
    }

    inner class HandlersInterfaceVisitor(
        val commandDependenciesProps: CommandDependencyProperties
    ) : KSDefaultVisitor<HandlersAndDependencies, Unit>() {

        override fun defaultHandler(node: KSNode, data: HandlersAndDependencies) {
            error(
                "Only interfaces can be annotated with @${HandlersInterface::class.simpleName}. " +
                    "$node is not a class"
            )
        }

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration,
            data: HandlersAndDependencies,
        ) {
            if (classDeclaration.classKind != ClassKind.INTERFACE) {
                error(
                    "Only interfaces can be annotated with @${HandlersInterface::class.simpleName}. " +
                        "$classDeclaration is a ${classDeclaration.classKind}"
                )
            }

            val handlerFunctions = classDeclaration.getAllUserFunctions()
            for (handlerFunction in handlerFunctions) {
                val handlerDeclaration =
                    handlerFunction.returnType?.resolve()?.declaration as? KSClassDeclaration
                if (handlerDeclaration != null) {
                    data.addHandler(
                        handlerDeclaration,
                        commandDependenciesProps,
                        handlerFactory,
                        logger,
                    )
                }
            }

            handlersInterfaces.add(classDeclaration)
        }
    }

    inner class ContainerInterfaceVisitor(
        val commandDependenciesProps: CommandDependencyProperties
    ) : KSDefaultVisitor<HandlersAndDependencies, Unit>() {
        override fun defaultHandler(node: KSNode, data: HandlersAndDependencies) {
            error("Only interfaces can be annotated with @${ContainerInterface::class.simpleName}")
        }

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration,
            data: HandlersAndDependencies,
        ) {
            if (classDeclaration.classKind != ClassKind.INTERFACE) {
                error(
                    "Only interfaces can be annotated with @${ContainerInterface::class.simpleName}. " +
                        "$classDeclaration is a ${classDeclaration.classKind}"
                )
            }

            val functionDependencies = classDeclaration.getAllUserFunctions()
            // TODO can we get generics here?
            for (functionDependency in functionDependencies) {
                val dependencyTypeRef = functionDependency.returnType ?: continue
                data.addDependency(
                    dependencyTypeRef,
                    commandDependenciesProps,
                    handlerFactory,
                    logger,
                    DependencyOverrideType.FUNCTIONAL,
                )
            }

            val propertyDependencies = classDeclaration.getAllProperties()
            for (propertyDependency in propertyDependencies) {
                data.addDependency(
                    propertyDependency.type,
                    commandDependenciesProps,
                    handlerFactory,
                    logger,
                    DependencyOverrideType.PROPERTY,
                )
            }

            containerInterfaces.add(classDeclaration)
        }
    }

    inner class DependencyIndexVisitor : KSDefaultVisitor<HandlersAndDependencies, Unit>() {
        override fun defaultHandler(node: KSNode, data: HandlersAndDependencies) {
            error("Only classes can be annotated with @${KbusIndex::class.simpleName}")
        }

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration,
            data: HandlersAndDependencies,
        ) {
            if (classDeclaration.classKind != ClassKind.CLASS) {
                error(
                    "Only classes can be annotated with @${KbusIndex::class.simpleName}. " +
                        "$classDeclaration is a ${classDeclaration.classKind}"
                )
            }

            val kbusIndexAnnotation =
                classDeclaration.annotations.find {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                        KbusIndex::class.qualifiedName
                } ?: error("Missing @${KbusIndex::class.simpleName} annotation")

            // TODO don't pass factory to data
            addDependencies(kbusIndexAnnotation, data)
            addHandlers(kbusIndexAnnotation, data)
        }

        private fun addDependencies(
            kbusIndexAnnotation: KSAnnotation,
            data: HandlersAndDependencies,
        ) {
            val dependenciesArg =
                kbusIndexAnnotation.arguments.find {
                    it.name?.asString() == KbusIndex::dependencies.name
                }

            @Suppress("UNCHECKED_CAST")
            val dependencyInfos = dependenciesArg?.value as? List<KSAnnotation> ?: emptyList()

            data.addIndexedDependencies(dependencyInfos, dependencyIndexFactory, logger)
        }

        private fun addHandlers(kbusIndexAnnotation: KSAnnotation, data: HandlersAndDependencies) {
            val handlersArg =
                kbusIndexAnnotation.arguments.find {
                    it.name?.asString() == KbusIndex::handlers.name
                }

            @Suppress("UNCHECKED_CAST")
            val handlerInfos = handlersArg?.value as? List<KSAnnotation> ?: emptyList()

            data.addIndexedHandlers(handlerInfos, dependencyIndexFactory, logger)
        }
    }
}

fun KSClassDeclaration.getAllUserFunctions(): Sequence<KSFunctionDeclaration> {
    return getFunctionsRecursively().filter { func ->
        func.simpleName.asString() !in setOf("equals", "hashCode", "toString") &&
            func.origin == Origin.KOTLIN || func.origin == Origin.JAVA
    }
}

/**
 * Recursively collects declared functions from the class and all its supertypes. This bypasses
 * KSP's getAllFunctions() limitations across module boundaries.
 */
fun KSClassDeclaration.getFunctionsRecursively(
    visited: MutableSet<KSClassDeclaration> = mutableSetOf()
): Sequence<KSFunctionDeclaration> {
    // 1. Avoid cycles or diamond inheritance duplicates
    if (!visited.add(this)) return emptySequence()

    return sequence {
        // 2. Yield functions declared strictly in this interface/class
        yieldAll(getDeclaredFunctions())

        // 3. Resolve and recurse into supertypes
        superTypes
            .map { it.resolve().declaration }
            .filterIsInstance<KSClassDeclaration>()
            .forEach { parentDeclaration ->
                yieldAll(parentDeclaration.getFunctionsRecursively(visited))
            }
    }
}
