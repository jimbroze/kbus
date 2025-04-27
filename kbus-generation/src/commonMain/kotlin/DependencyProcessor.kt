package com.jimbroze.kbus.generation

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability
import kotlin.collections.orEmpty
import kotlin.reflect.KClass
import kotlinx.datetime.Clock

// Should we only return a dependencyDef for most part and then only calculate LoaderDep for
// abstract loader?
// FIXME isSingleton. Do handlers need to be singletons?
// FIXME name should be on loaderDep?
data class DependencyDefinition(
    val declaration: KSDeclaration,
    val typeArgs: List<KSTypeArgument>,
    val name: String,
    val nullability: Nullability = Nullability.NOT_NULL,
) {
    companion object {
        fun withCustomName(
            declaration: KSDeclaration,
            typeArgs: List<KSTypeArgument>,
            customName: String? = null,
            nullability: Nullability = Nullability.NOT_NULL,
        ): DependencyDefinition {
            val name =
                customName ?: declaration.simpleName.asString().replaceFirstChar { it.lowercase() }

            return DependencyDefinition(
                declaration,
                typeArgs,
                name = name,
                nullability = nullability,
            )
        }

        fun fromParameter(
            parameter: KSValueParameter,
            useParamName: Boolean,
        ): DependencyDefinition {
            val type = parameter.type.resolve()
            val typeArgs = parameter.type.element?.typeArguments.orEmpty()

            val customName = if (useParamName) parameter.name?.asString() else null

            return withCustomName(
                type.declaration,
                typeArgs,
                customName = customName,
                nullability = type.nullability,
            )
        }
    }

    fun getTypeWithArgs(): String {
        val typeName = StringBuilder(declaration.qualifiedName!!.asString())

        for (typeArg in typeArgs) {
            val type = typeArg.type?.resolve()
            val typeText = type?.declaration?.qualifiedName?.asString() ?: continue
            val variance = typeArg.variance.label
            val nullability = if (type.nullability == Nullability.NULLABLE) "?" else ""

            typeName.append("<$variance $typeText $nullability>")
        }
        if (nullability == Nullability.NULLABLE) typeName.append("?")

        return typeName.toString()
    }
}

data class LoaderDependency(
    val definition: DependencyDefinition,
    val isRoot: Boolean,
    val isSingleton: Boolean = true,
) {
    // FIXME should name be on this class instead?
    fun equalsDependency(other: LoaderDependency): Boolean {
        return other !== this &&
            other.definition.declaration == this.definition.declaration &&
            other.definition.typeArgs == this.definition.typeArgs &&
            other.definition.nullability == this.definition.nullability
    }
}

// DependencyFactory?
@Suppress("unused")
class DependencyProcessor(private val busPackageName: String, private val logger: KSPLogger) {
    fun generateFrom(type: KSType, includeNested: Boolean): Set<LoaderDependency> {
        return extractedDependenciesOrNull(type, includeNested = includeNested) ?: emptySet()
    }

    fun generateFrom(
        properties: Sequence<KSPropertyDeclaration>,
        includeNested: Boolean,
    ): Set<LoaderDependency> {
        // TODO also go through functions?

        return properties
            .flatMap { prop ->
                extractedDependenciesOrNull(
                    dependency = prop.type.resolve(),
                    customName = prop.simpleName.asString(),
                    typeArgs = prop.type.element?.typeArguments.orEmpty(),
                    includeNested,
                ) ?: emptySet()
            }
            .toSet()
    }

    private fun extractedDependenciesOrNull(
        dependency: KSType,
        customName: String? = null,
        typeArgs: List<KSTypeArgument> = emptyList(),
        includeNested: Boolean = true,
    ): Set<LoaderDependency>? {
        val depDeclaration = dependency.declaration

        if (cannotBeRoot(depDeclaration)) {
            return null
        }

        val dependencyDefinition =
            DependencyDefinition.withCustomName(
                depDeclaration,
                typeArgs,
                customName = customName,
                nullability = dependency.nullability,
            )

        val nested = nestedDependencies(depDeclaration)
        val allDependencies =
            if (nested === null) {
                setOf(LoaderDependency(dependencyDefinition, isRoot = true))
            } else {
                if (includeNested) nested + LoaderDependency(dependencyDefinition, isRoot = false)
                else setOf(LoaderDependency(dependencyDefinition, isRoot = false))
            }

        return allDependencies
    }

    private fun cannotBeRoot(depDeclaration: KSDeclaration): Boolean {
        val cannotBeRootPackages = listOf("kotlin", "kotlinx.datetime")
        val cannotBeRootExceptions = listOf<KClass<out Any>>(Clock::class)

        return cannotBeRootPackages.contains(depDeclaration.packageName.asString()) &&
            cannotBeRootExceptions.none {
                depDeclaration.qualifiedName!!.asString() == it.qualifiedName
            }
    }

    private fun nestedDependencies(depDeclaration: KSDeclaration): MutableSet<LoaderDependency>? {
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
    ): MutableSet<LoaderDependency>? {
        val allDependencies = mutableSetOf<LoaderDependency>()

        for (dependency in classDeclaration.primaryConstructor?.parameters.orEmpty()) {
            extractedDependenciesOrNull(
                    dependency.type.resolve(),
                    typeArgs = dependency.type.element?.typeArguments.orEmpty(),
                )
                ?.let { allDependencies.addAll(it) } ?: return null
        }

        return allDependencies
    }
}
