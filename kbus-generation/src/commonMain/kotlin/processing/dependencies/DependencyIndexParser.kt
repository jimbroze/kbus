package com.jimbroze.kbus.generation.processing.dependencies

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSType
import com.jimbroze.kbus.annotations.DependencyInfo
import com.jimbroze.kbus.annotations.HandlerInfo
import com.jimbroze.kbus.generation.processing.TypeResolver
import com.jimbroze.kbus.generation.processing.handlers.HandlerData
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import kotlin.reflect.KProperty1

class DependencyIndexParser(@Suppress("unused") private val logger: KSPLogger) {
    fun createDependencies(dependencyInfoAnnotations: List<KSAnnotation>): Dependencies {
        val dependenciesWithDehydratedChildren = mutableSetOf<DependencyWithDehydratedChildren>()
        for (dependencyInfoAnnotation in dependencyInfoAnnotations) {
            val dependency = createDependency(dependencyInfoAnnotation)
            dependenciesWithDehydratedChildren.add(dependency)
        }

        return IndexDependencies(dependenciesWithDehydratedChildren)
    }

    fun createHandlerFromAnnotation(
        handlerInfoAnnotation: KSAnnotation,
        allDependencies: Set<Dependency>,
    ): HandlerDefinition {
        val allDependenciesBySignature =
            allDependencies.associateBy { dependency -> dependency.signature }
        return createHandler(handlerInfoAnnotation, allDependenciesBySignature)
            ?: error("Could not create a valid handler definition from the provided annotation")
    }

    private fun createDependency(
        dependencyInfoAnnotation: KSAnnotation
    ): DependencyWithDehydratedChildren {
        require(
            dependencyInfoAnnotation.annotationType
                .resolve()
                .declaration
                .qualifiedName
                ?.asString() == DependencyInfo::class.qualifiedName
        ) {
            "Only DependencyInfo annotations can be processed here"
        }

        val signature: String = dependencyInfoAnnotation.findArgument(DependencyInfo::signature)
        val requiresCommandDependencies: Boolean =
            dependencyInfoAnnotation.findArgument(DependencyInfo::requiresCommandDependencies)
        val cannotBeAutoloaded: Boolean =
            dependencyInfoAnnotation.findArgument(DependencyInfo::cannotBeAutoloaded)
        val topLevelDependencies: List<String> =
            dependencyInfoAnnotation.findArgument(DependencyInfo::topLevelDependencies)
        val typeOfDependencySymbol: KSType =
            dependencyInfoAnnotation.findArgument(DependencyInfo::dependencyType)

        val dependencyTypeName = TypeResolver.resolve(signature)

        val typeOfDependency =
            com.jimbroze.kbus.annotations.DependencyType.valueOf(
                typeOfDependencySymbol.declaration.simpleName.asString()
            )

        val metadata =
            Dependency.fromDependencyType(
                typeOfDependency,
                dependencyTypeName,
                requiresCommandDependencies,
            )

        return DependencyWithDehydratedChildren(metadata, topLevelDependencies, cannotBeAutoloaded)
    }

    private fun createHandler(
        handlerInfoAnnotation: KSAnnotation,
        allDependenciesBySignature: Map<String, Dependency>,
    ): HandlerDefinition? {
        require(
            handlerInfoAnnotation.annotationType.resolve().declaration.qualifiedName?.asString() ==
                HandlerInfo::class.qualifiedName
        ) {
            "Only HandlerInfo annotations can be processed here"
        }

        val messageClassSignature: String =
            handlerInfoAnnotation.findArgument(HandlerInfo::messageClass)
        val handlerClassSignature: String =
            handlerInfoAnnotation.findArgument(HandlerInfo::handlerClass)
        val returnTypeSignature: String =
            handlerInfoAnnotation.findArgument(HandlerInfo::returnType)
        val topLevelDependenciesSignatures: List<String> =
            handlerInfoAnnotation.findArgument(HandlerInfo::topLevelDependencies)
        val typeOfHandlerSymbol: KSType =
            handlerInfoAnnotation.findArgument(HandlerInfo::handlerType)

        val messageClass = TypeResolver.resolveClassName(messageClassSignature)
        val handlerClass = TypeResolver.resolveClassName(handlerClassSignature)
        val returnType = TypeResolver.resolve(returnTypeSignature)

        val typeOfHandler =
            com.jimbroze.kbus.annotations.HandlerType.valueOf(
                typeOfHandlerSymbol.declaration.simpleName.asString()
            )

        val topLevelDependencies =
            topLevelDependenciesSignatures.map { allDependenciesBySignature.getValue(it) }

        val handlerData = HandlerData(handlerClass, messageClass, returnType, topLevelDependencies)

        return HandlerDefinition.createFromType(typeOfHandler, handlerData, logger)
    }
}

inline fun <reified T> KSAnnotation.findArgument(property: KProperty1<*, *>): T {
    val argument =
        arguments.find { it.name?.asString() == property.name }
            ?: error("Argument '${property.name}' is missing")

    val value = argument.value ?: error("Argument '${property.name}' has a null value")

    return value as? T
        ?: error(
            "Argument '${property.name}' expected '${T::class.simpleName}' " +
                "but KSP returned '${value::class.simpleName}'"
        )
}

private data class DependencyWithDehydratedChildren(
    val metadata: Dependency,
    val topLevelDependencySignatures: List<String>,
    val cannotBeAutoloaded: Boolean,
) {
    fun withHydratedChildren(topLevelDependencies: List<Dependency>): DependencyWithChildren {
        return DependencyWithChildren(metadata, topLevelDependencies, cannotBeAutoloaded)
    }
}

private class IndexDependencies(dependencies: Set<DependencyWithDehydratedChildren>) :
    Dependencies {
    private val allDependenciesMetadata = dependencies.map { it.metadata }
    override val topLevelDependencies = allDependenciesMetadata

    private val dependenciesBySignature = allDependenciesMetadata.associateBy { it.signature }
    override val allDependencies =
        dependencies
            .map { dependency ->
                val topLevelDependencies =
                    dependency.topLevelDependencySignatures.map { topLevelDependencySignature ->
                        dependenciesBySignature.getValue(topLevelDependencySignature)
                    }
                dependency.withHydratedChildren(topLevelDependencies)
            }
            .toSet()
}
