package com.jimbroze.kbus.generation.processing.dependencies

import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.squareup.kotlinpoet.TypeName
import kotlin.reflect.KClass

sealed interface Dependency {
    val requiresCommandDependencies: Boolean

    val name: String
        get() = NameGenerator.getNameForType(typeName)

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
