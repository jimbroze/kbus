package com.jimbroze.kbus.generation.processing.dependencies

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSType
import com.jimbroze.kbus.annotations.DependencyInfo
import com.jimbroze.kbus.generation.processing.TypeResolver
import kotlin.reflect.KProperty1

class DependencyIndexFactory(@Suppress("unused") private val logger: KSPLogger) {
    fun createDependencies(
        dependencyInfoAnnotations: List<KSAnnotation>,
        resolver: Resolver,
    ): Dependencies {
        val allDependencies = mutableSetOf<Dependency>()
        val dependenciesWithDehydratedChildren = mutableSetOf<DependencyWithDehydratedChildren>()
        for (dependencyInfoAnnotation in dependencyInfoAnnotations) {
            val dependency = createDependency(dependencyInfoAnnotation, resolver)
            dependenciesWithDehydratedChildren.add(dependency)
            allDependencies.add(dependency.metadata)
        }

        return IndexDependencies(dependenciesWithDehydratedChildren)
    }

    private fun createDependency(
        dependencyInfoAnnotation: KSAnnotation,
        resolver: Resolver,
    ): DependencyWithDehydratedChildren {
        if (
            dependencyInfoAnnotation.annotationType
                .resolve()
                .declaration
                .qualifiedName
                ?.asString() != DependencyInfo::class.qualifiedName
        )
            error("Only DependencyInfo annotations can be processed here")

        val type: String = dependencyInfoAnnotation.findArgument(DependencyInfo::type)
        val requiresCommandDependencies: Boolean =
            dependencyInfoAnnotation.findArgument(DependencyInfo::requiresCommandDependencies)
        val cannotBeAutoloaded: Boolean =
            dependencyInfoAnnotation.findArgument(DependencyInfo::cannotBeAutoloaded)
        val topLevelDependencies: List<String> =
            dependencyInfoAnnotation.findArgument(DependencyInfo::topLevelDependencies)
        val typeOfDependencySymbol: KSType =
            dependencyInfoAnnotation.findArgument(DependencyInfo::dependencyType)

        val dependencyType = TypeResolver.resolve(type, resolver)

        val typeOfDependency =
            com.jimbroze.kbus.annotations.DependencyType.valueOf(
                typeOfDependencySymbol.declaration.simpleName.asString()
            )

        val metadata =
            Dependency.fromDependencyType(
                typeOfDependency,
                SafeType(dependencyType),
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
    fun hydrateChildren(topLevelDependencies: List<Dependency>): DependencyWithChildren {
        return DependencyWithChildren(metadata, topLevelDependencies, cannotBeAutoloaded)
    }
}

private class IndexDependencies(dependencies: Set<DependencyWithDehydratedChildren>) :
    Dependencies {
    override val topLevelDependencies = dependencies.map { it.metadata }

    private val dependenciesBySignature = topLevelDependencies.associateBy { it.signature }
    override val allDependencies =
        dependencies
            .map { dependency ->
                val topLevelDependencies =
                    dependency.topLevelDependencySignatures.map {
                        dependenciesBySignature.getValue(dependency.metadata.signature)
                    }
                dependency.hydrateChildren(topLevelDependencies)
            }
            .toSet()
}
