package com.jimbroze.kbus.generation.processing.dependencies

import com.google.devtools.ksp.symbol.KSType
import com.jimbroze.kbus.core.uow.CommandDependencies
import kotlin.reflect.KClass

enum class DependencyType {
    PROPERTY,
    FUNCTIONAL,
}

// TODO names for same declaration with different type args
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

    fun hasConflictingNameWith(other: Dependency): Boolean {
        return this.name == other.name && this != other
    }

    val name: String
        get() = typeRef.declaration.simpleName.asString().replaceFirstChar { it.lowercase() }

    val typeRef: KSType
    val prefix: String
        get() = ""

    val accessReference: String
        get() = "$prefix$name"
}

data class PropertyDependency(override val typeRef: KSType) : Dependency

data class FunctionalDependency(
    override val typeRef: KSType,
    private val requiresCommandDependencies: Boolean,
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
}

data class NonDependency(override val typeRef: KSType) : Dependency {
    override val prefix
        get() = error("This dependency should not be used: $typeRef")
}
