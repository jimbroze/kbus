package com.jimbroze.kbus.generation.processors

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
import com.jimbroze.kbus.annotations.DependencyIndex
import com.jimbroze.kbus.annotations.HandlersInterface
import com.jimbroze.kbus.generation.generators.AutoLoaderGenerator
import com.jimbroze.kbus.generation.generators.BusGenerator
import com.jimbroze.kbus.generation.generators.ContainerInterfaceGenerator
import com.jimbroze.kbus.generation.generators.HandlersFactoryGenerator
import com.jimbroze.kbus.generation.generators.HandlersInterfaceGenerator
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependencyProperties
import com.jimbroze.kbus.generation.processing.dependencies.DependencyIndexFactory
import com.jimbroze.kbus.generation.processing.dependencies.DependencyOverrideType
import com.jimbroze.kbus.generation.processing.handlers.HandlerFactory
import com.jimbroze.kbus.generation.processors.visitors.HandlersAndDependencies

class ContainerGenerators(
    val containerInterface: ContainerInterfaceGenerator,
    val handlersInterface: HandlersInterfaceGenerator,
    val autoLoader: AutoLoaderGenerator,
    val handlersFactory: HandlersFactoryGenerator,
    val bus: BusGenerator,
)

class DependencyProcessor(
    @Suppress("unused") private val logger: KSPLogger,
    private val handlerFactory: HandlerFactory,
    private val dependencyIndexFactory: DependencyIndexFactory,
    private val generators: ContainerGenerators,
) : SymbolProcessor {
    private val dependencies = HandlersAndDependencies()
    private val handlersInterfaces = mutableSetOf<KSClassDeclaration>()
    private val containerInterfaces = mutableSetOf<KSClassDeclaration>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val commandDependenciesProps = CommandDependencyProperties.fromResolver(resolver)

        val dependencyIndexes =
            resolver.getSymbolsWithAnnotation(DependencyIndex::class.qualifiedName.toString())
        val (validIndexSymbols, invalidIndexSymbols) = dependencyIndexes.partition { it.validate() }
        validIndexSymbols.forEach { it.accept(IndexVisitor(), dependencies) }

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

        return invalidIndexSymbols + invalidContainerSymbols + invalidHandlerSymbols
    }

    /**
     * Use finish function as this 'consumes' interfaces produced by other processors. Ensure that
     * other processors have run before this
     */
    override fun finish() {
        if (dependencies.isEmpty()) return

        val generatedPackagePath = "com.jimbroze.kbus.generated"

        generators.containerInterface.generateCombinedInterface(
            generatedPackagePath,
            this.containerInterfaces,
        )
        generators.handlersInterface.generateCombinedInterface(
            generatedPackagePath,
            this.handlersInterfaces,
        )
        generators.autoLoader.generateAutoloader(generatedPackagePath, dependencies.allDependencies)
        generators.handlersFactory.generateClass(generatedPackagePath, dependencies.handlers)
        generators.bus.generateClass(generatedPackagePath, dependencies.handlers)
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

    inner class IndexVisitor : KSDefaultVisitor<HandlersAndDependencies, Unit>() {
        override fun defaultHandler(node: KSNode, data: HandlersAndDependencies) {
            error("@${DependencyIndex::class.simpleName} must be a class")
        }

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration,
            data: HandlersAndDependencies,
        ) {
            if (classDeclaration.classKind != ClassKind.CLASS) {
                error(
                    "Only classes can be annotated with @${ContainerInterface::class.simpleName}. " +
                        "$classDeclaration is a ${classDeclaration.classKind}"
                )
            }

            val indexAnnotation =
                classDeclaration.annotations.find {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                        DependencyIndex::class.qualifiedName
                } ?: return

            val dependenciesArg =
                indexAnnotation.arguments.find {
                    it.name?.asString() == DependencyIndex::dependencies.name
                }

            @Suppress("UNCHECKED_CAST")
            val dependencyInfos = dependenciesArg?.value as? List<KSAnnotation> ?: emptyList()

            data.addIndexedDependencies(dependencyInfos, dependencyIndexFactory, logger)
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
