package com.jimbroze.kbus.generation.processing.dependencies

import com.google.devtools.ksp.symbol.KSType
import com.jimbroze.kbus.annotations.DependencyType
import com.jimbroze.kbus.core.uow.CommandDependencies
import com.squareup.kotlinpoet.ksp.toTypeName
import kotlin.reflect.KClass

// TODO throw error if override but not creating dependency
enum class DependencyOverrideType {
    PROPERTY,
    FUNCTIONAL,
}

class SafeType(val original: KSType) {
    private val identity = original.toTypeName()

    override fun equals(other: Any?) = (other is SafeType) && identity == other.identity

    override fun hashCode() = identity.hashCode()

    override fun toString() = identity.toString()
}

sealed interface Dependency {
    companion object {
        fun fromDependencyOverrideType(
            dependencyOverrideType: DependencyOverrideType,
            safeType: SafeType,
            requiresCommandDependencies: Boolean,
        ): Dependency {
            val dependencyType =
                when (dependencyOverrideType) {
                    DependencyOverrideType.PROPERTY -> DependencyType.PROPERTY
                    DependencyOverrideType.FUNCTIONAL -> DependencyType.FUNCTIONAL
                }

            return fromDependencyType(dependencyType, safeType, requiresCommandDependencies)
        }

        fun fromDependencyType(
            dependencyType: DependencyType,
            safeType: SafeType,
            requiresCommandDependencies: Boolean,
        ): Dependency {
            return when (dependencyType) {
                DependencyType.PROPERTY -> PropertyDependency(safeType)
                DependencyType.FUNCTIONAL ->
                    FunctionalDependency(safeType, requiresCommandDependencies)
                DependencyType.COMMAND -> CommandDependency(safeType)
                DependencyType.NON_DEPENDENCY -> NonDependency(safeType)
            }
        }
    }

    val requiresCommandDependencies: Boolean

    // TODO combine with dep factory naming function. Combine interfaces??
    val name: String
        get() = getNameForType(typeRef)

    val signature: String
        get() = typeRef.toTypeName().toString()

    val safeType: SafeType
    val typeRef: KSType
        get() = safeType.original

    val prefix: String
        get() = ""

    val accessReference: String
        get() = "$prefix$name"

    fun hasConflictingNameWith(other: Dependency): Boolean {
        return this.name == other.name && this != other
    }

    private fun getNameForType(type: KSType, isNested: Boolean = false): String {
        val declarationName = type.declaration.simpleName.asString()

        val typeArgumentsString =
            type.arguments
                .takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "And", prefix = "Of") { arg ->
                    arg.type?.resolve()?.let { getNameForType(it, true) } ?: ""
                } ?: ""

        return declarationName.replaceFirstChar {
            if (isNested) it.uppercase() else it.lowercase()
        } + typeArgumentsString
    }
}

data class PropertyDependency(override val safeType: SafeType) : Dependency {
    override val requiresCommandDependencies = false
}

data class FunctionalDependency(
    override val safeType: SafeType,
    override val requiresCommandDependencies: Boolean,
) : Dependency {
    data class DependencyConstructorParameters(val name: String, val typeRef: KClass<*>)

    val functionParameters: List<DependencyConstructorParameters>
        get() =
            if (requiresCommandDependencies)
                listOf(
                    DependencyConstructorParameters(
                        "commandDependencies",
                        CommandDependencies::class,
                    )
                )
            else emptyList()

    override val accessReference: String
        get() {
            val constructorArgNames = this.functionParameters.joinToString(", ") { it.name }
            return "$prefix$name($constructorArgNames)"
        }
}

data class CommandDependency(override val safeType: SafeType) : Dependency {
    override val prefix = "commandDependencies."
    override val requiresCommandDependencies = false
}

data class NonDependency(override val safeType: SafeType) : Dependency {
    override val prefix
        get() = error("This dependency should not be used: $typeRef")

    override val requiresCommandDependencies = false
}
