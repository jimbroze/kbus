package com.jimbroze.kbus.generation

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import kotlin.collections.orEmpty
import kotlin.reflect.KClass
import kotlinx.datetime.Clock

// DependencyFactory?
@Suppress("unused")
class DependencyProcessor(private val busPackageName: String, private val logger: KSPLogger) {
    fun generateFrom(type: KSType, includeNested: Boolean): Set<NestedDependency> {
        return createDependencies(type, includeNested = includeNested) ?: emptySet()
    }

    fun generateFrom(
        properties: Sequence<KSPropertyDeclaration>,
        includeNested: Boolean,
    ): Set<NestedDependency> {
        // TODO also go through functions?

        return properties
            .flatMap { prop ->
                createDependencies(
                    dependency = prop.type.resolve(),
                    customName = prop.simpleName.asString(),
                    typeArgs = prop.type.element?.typeArguments.orEmpty(),
                    includeNested,
                ) ?: emptySet()
            }
            .toSet()
    }

    private fun createDependencies(
        dependency: KSType,
        customName: String? = null,
        typeArgs: List<KSTypeArgument> = emptyList(),
        includeNested: Boolean = true,
    ): Set<NestedDependency>? {
        if (cannotBeDependency(dependency.declaration)) {
            return null
        }

        val nested = nestedDependencies(dependency.declaration)

        val loaderDependency =
            NestedDependency.fromDependency(
                Dependency.withCustomName(
                    dependency.declaration,
                    typeArgs,
                    customName = customName,
                    nullability = dependency.nullability,
                ),
                isRoot = nested === null,
            )

        val allDependencies = mutableSetOf(loaderDependency)
        if (nested !== null && includeNested) {
            allDependencies.addAll(nested)
        }

        return allDependencies
    }

    private fun cannotBeDependency(depDeclaration: KSDeclaration): Boolean {
        val nonDependencyPackages = listOf("kotlin", "kotlinx.datetime")
        val canBeDependency = listOf<KClass<out Any>>(Clock::class)

        return nonDependencyPackages.contains(depDeclaration.packageName.asString()) &&
            canBeDependency.none { depDeclaration.qualifiedName!!.asString() == it.qualifiedName }
    }

    private fun nestedDependencies(depDeclaration: KSDeclaration): MutableSet<NestedDependency>? {
        val hasNestedDependencies =
            depDeclaration is KSClassDeclaration &&
                depDeclaration.primaryConstructor?.parameters.isNullOrEmpty().not() &&
                depDeclaration.packageName.asString() != busPackageName

        // TODO prevent calculating nested if extractNested is false? Probably can't do this? At
        // least prevent recursion?
        val nestedDependencies =
            if (hasNestedDependencies) {
                nestedDependenciesOrNull(depDeclaration)
            } else {
                null
            }
        return nestedDependencies
    }

    private fun nestedDependenciesOrNull(
        classDeclaration: KSClassDeclaration
    ): MutableSet<NestedDependency>? {
        val allDependencies = mutableSetOf<NestedDependency>()

        for (dependency in classDeclaration.primaryConstructor?.parameters.orEmpty()) {
            createDependencies(
                    dependency.type.resolve(),
                    typeArgs = dependency.type.element?.typeArguments.orEmpty(),
                )
                ?.let { allDependencies.addAll(it) } ?: return null
        }

        return allDependencies
    }
}
