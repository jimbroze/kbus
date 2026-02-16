package com.jimbroze.kbus.generation.processing.dependencies

import com.jimbroze.kbus.annotations.DependencyType
import com.jimbroze.kbus.core.uow.CommandDependencies
import com.squareup.kotlinpoet.TypeName
import kotlin.reflect.KClass

// TODO throw error if override but not creating dependency
enum class DependencyOverrideType {
    PROPERTY,
    FUNCTIONAL,
}

sealed interface Dependency {
    companion object {
        fun fromDependencyOverrideType(
            dependencyOverrideType: DependencyOverrideType,
            typeName: TypeName,
            requiresCommandDependencies: Boolean,
        ): Dependency {
            val dependencyType =
                when (dependencyOverrideType) {
                    DependencyOverrideType.PROPERTY -> DependencyType.PROPERTY
                    DependencyOverrideType.FUNCTIONAL -> DependencyType.FUNCTIONAL
                }

            return fromDependencyType(dependencyType, typeName, requiresCommandDependencies)
        }

        fun fromDependencyType(
            dependencyType: DependencyType,
            typeRef: TypeName,
            requiresCommandDependencies: Boolean,
        ): Dependency {
            return when (dependencyType) {
                DependencyType.PROPERTY -> PropertyDependency(typeRef)
                DependencyType.FUNCTIONAL ->
                    FunctionalDependency(typeRef, requiresCommandDependencies)
                DependencyType.COMMAND -> CommandDependency(typeRef)
                DependencyType.NON_DEPENDENCY -> NonDependency(typeRef)
            }
        }
    }

    val requiresCommandDependencies: Boolean

    // TODO combine with dep factory naming function. Combine interfaces??
    val name: String
        get() = getNameForType(typeName)

    val signature: String
        get() = typeName.toString()

    val typeName: TypeName

    val prefix: String
        get() = ""

    val accessReference: String
        get() = "$prefix$name"

    fun hasConflictingNameWith(other: Dependency): Boolean {
        return this.name == other.name && this != other
    }

    private fun getNameForType(type: TypeName, isNested: Boolean = false): String =
        NameGenerator.getNameForType(type, isNested)
}

data class PropertyDependency(override val typeName: TypeName) : Dependency {
    override val requiresCommandDependencies = false
}

data class FunctionalDependency(
    override val typeName: TypeName,
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

data class CommandDependency(override val typeName: TypeName) : Dependency {
    override val prefix = "commandDependencies."
    override val requiresCommandDependencies = false
}

data class NonDependency(override val typeName: TypeName) : Dependency {
    override val prefix
        get() = error("This dependency should not be used: $typeName")

    override val accessReference: String
        get() = error("This dependency should not be used: $typeName")

    override val requiresCommandDependencies = false
}
