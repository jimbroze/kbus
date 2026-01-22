package com.jimbroze.kbus.generation.processing.dependencies

import com.google.devtools.ksp.symbol.KSType
import com.jimbroze.kbus.core.uow.CommandDependencies
import kotlin.reflect.KClass

// TODO throw error if override but not creating dependency
enum class DependencyType {
    PROPERTY,
    FUNCTIONAL,
}

sealed interface Dependency {
    companion object {
        fun fromDependencyType(
            dependencyType: DependencyType,
            typeRef: KSType,
            requiresCommandDependencies: Boolean,
        ): Dependency {
            return when (dependencyType) {
                DependencyType.PROPERTY -> PropertyDependency(typeRef)
                DependencyType.FUNCTIONAL ->
                    FunctionalDependency(typeRef, requiresCommandDependencies)
            }
        }
    }

    val requiresCommandDependencies: Boolean

    // TODO combine with dep factory naming function. Combine interfaces??
    val name: String
        get() = getNameForType(typeRef)

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

    val typeRef: KSType
    val prefix: String
        get() = ""

    val accessReference: String
        get() = "$prefix$name"
}

data class PropertyDependency(override val typeRef: KSType) : Dependency {
    override val requiresCommandDependencies = false
}

data class FunctionalDependency(
    override val typeRef: KSType,
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

data class CommandDependency(override val typeRef: KSType) : Dependency {
    override val prefix = "commandDependencies."
    override val requiresCommandDependencies = false
}

data class NonDependency(override val typeRef: KSType) : Dependency {
    override val prefix
        get() = error("This dependency should not be used: $typeRef")

    override val requiresCommandDependencies = false
}
