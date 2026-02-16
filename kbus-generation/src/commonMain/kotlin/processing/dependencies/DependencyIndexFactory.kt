package com.jimbroze.kbus.generation.processing.dependencies

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSType
import com.jimbroze.kbus.annotations.DependencyInfo
import com.jimbroze.kbus.generation.processing.TypeResolver
import kotlin.reflect.KProperty1

class DependencyIndexFactory(@Suppress("unused") private val logger: KSPLogger) {
    fun createDependencies(dependencyInfoAnnotations: List<KSAnnotation>): Dependencies {
        val dependenciesWithDehydratedChildren = mutableSetOf<DependencyWithDehydratedChildren>()
        for (dependencyInfoAnnotation in dependencyInfoAnnotations) {
            val dependency = createDependency(dependencyInfoAnnotation)
            dependenciesWithDehydratedChildren.add(dependency)
        }

        return IndexDependencies(dependenciesWithDehydratedChildren)
    }

    private fun createDependency(
        dependencyInfoAnnotation: KSAnnotation
    ): DependencyWithDehydratedChildren {
        if (
            dependencyInfoAnnotation.annotationType
                .resolve()
                .declaration
                .qualifiedName
                ?.asString() != DependencyInfo::class.qualifiedName
        )
            error("Only DependencyInfo annotations can be processed here")

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

// private fun KSAnnotation.findArgument(property: kotlin.reflect.KProperty1<*, *>) =
//    arguments.find { it.name?.asString() == property.name }?.value
//
// private fun KSAnnotation.findStringArgument(property: kotlin.reflect.KProperty1<*, *>) =
//    this.findArgument(property) as? String ?: error("Missing ${property.name} argument")

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
