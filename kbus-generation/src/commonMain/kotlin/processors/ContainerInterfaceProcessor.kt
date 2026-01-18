package com.jimbroze.kbus.generation.processors

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.validate
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.jimbroze.kbus.annotations.ContainerInterface
import com.jimbroze.kbus.annotations.HandlersInterface
import com.jimbroze.kbus.generation.generators.AutoLoaderGenerator
import com.jimbroze.kbus.generation.generators.BusGenerator
import com.jimbroze.kbus.generation.generators.ContainerInterfaceGenerator
import com.jimbroze.kbus.generation.generators.HandlersFactoryGenerator
import com.jimbroze.kbus.generation.generators.HandlersInterfaceGenerator
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependencyProperties
import com.jimbroze.kbus.generation.processing.dependencies.DependencyFactory
import com.jimbroze.kbus.generation.processing.handlers.HandlerFactory
import com.jimbroze.kbus.generation.processors.visitors.HandlersContext

class ContainerInterfaceProcessor(
    private val logger: KSPLogger,
    private val handlerFactory: HandlerFactory,
    private val dependencyFactory: DependencyFactory,
    private val containerInterfaceGenerator: ContainerInterfaceGenerator,
    private val handlersInterfaceGenerator: HandlersInterfaceGenerator,
    private val autoLoaderGenerator: AutoLoaderGenerator,
    private val handlersFactoryGenerator: HandlersFactoryGenerator,
    private val busGenerator: BusGenerator,
) : SymbolProcessor {
    private val dependencies = HandlersContext()
    private val handlersInterfaces = mutableSetOf<KSClassDeclaration>()
    private val containerInterfaces = mutableSetOf<KSClassDeclaration>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val commandDependenciesProps = CommandDependencyProperties.fromResolver(resolver)

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

        return invalidContainerSymbols + invalidHandlerSymbols
    }

    /**
     * Use finish function as this 'consumes' interfaces produced by other processors. Ensure that
     * other processors have run before this
     */
    override fun finish() {
        if (dependencies.isEmpty()) return

        // TODO change to user-provided package name with fallback
        val generatedPackagePath = "com.jimbroze.kbus.generated"

        containerInterfaceGenerator.generateCombinedInterface(
            generatedPackagePath,
            this.containerInterfaces,
        )
        handlersInterfaceGenerator.generateCombinedInterface(
            generatedPackagePath,
            this.handlersInterfaces,
        )
        autoLoaderGenerator.generateAutoloader(generatedPackagePath, dependencies.allDependencies)
        handlersFactoryGenerator.generateClass(generatedPackagePath, dependencies.handlers)
        busGenerator.generateClass(generatedPackagePath, dependencies.handlers)
    }

    //    private fun validateNoDuplicates(
    //        allDependencies: MutableSet<NestedDependency>,
    //        dependency: NestedDependency,
    //    ) {
    //        val matches = allDependencies.filter { other -> dependency.isDuplicateOf(other) }
    //        if (matches.isNotEmpty()) {
    //            val dependencyName = dependency.declaration.simpleName.asString()
    //            logger.error(
    //                "Tried to generate multiple dependencies for $dependencyName",
    //                dependency.declaration,
    //            )
    //        }
    //    }

    inner class HandlersInterfaceVisitor(
        val commandDependenciesProps: CommandDependencyProperties
    ) : KSDefaultVisitor<HandlersContext, Unit>() {

        override fun defaultHandler(node: KSNode, data: HandlersContext) {
            error(
                "Only interfaces can be annotated with @${HandlersInterface::class.simpleName}. " +
                    "$node is not a class"
            )
        }

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration,
            data: HandlersContext,
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
                    data.addHandler(handlerDeclaration, commandDependenciesProps, handlerFactory)
                }
            }

            handlersInterfaces.add(classDeclaration)
        }
    }

    inner class ContainerInterfaceVisitor(
        val commandDependenciesProps: CommandDependencyProperties
    ) : KSDefaultVisitor<HandlersContext, Unit>() {
        override fun defaultHandler(node: KSNode, data: HandlersContext) {
            error("Only interfaces can be annotated with @${ContainerInterface::class.simpleName}")
        }

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration,
            data: HandlersContext,
        ) {
            if (classDeclaration.classKind != ClassKind.INTERFACE) {
                error(
                    "Only interfaces can be annotated with @${ContainerInterface::class.simpleName}. " +
                        "$classDeclaration is a ${classDeclaration.classKind}"
                )
            }

            // TODO override dependency type (fun/val/name) using interface
            // TODO getAllUserFunctions???
            val functionDependencies = classDeclaration.getAllFunctions()
            for (functionDependency in functionDependencies) {
                val dependencyTypeRef = functionDependency.returnType ?: continue
                data.addDependency(dependencyTypeRef, commandDependenciesProps, handlerFactory)
            }

            val propertyDependencies = classDeclaration.getAllProperties()
            for (propertyDependency in propertyDependencies) {
                data.addDependency(
                    propertyDependency.type,
                    commandDependenciesProps,
                    handlerFactory,
                )
            }

            containerInterfaces.add(classDeclaration)
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
