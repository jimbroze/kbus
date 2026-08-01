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

/**
 * A dependency built from the command's own [CommandDependencies] rather than from the container,
 * so it never appears in the generated dependency interface or autoloader.
 */
sealed interface CommandScopedDependency : Dependency

data class CommandDependency(override val typeName: TypeName) : CommandScopedDependency {
    override val prefix = "commandDependencies."
    override val requiresCommandDependencies = false
}

/**
 * A handler's parameter of a generated per-context command executor interface. Its access
 * expression depends on the owning context of the handler being built, which only the factory
 * generating that handler knows, so it has none of its own.
 */
data class ContextCommandsDependency(override val typeName: TypeName) : CommandScopedDependency {
    override val requiresCommandDependencies = false

    override val prefix
        get() = error("A context command executor is constructed by its context's factory")

    override val accessReference: String
        get() = error("A context command executor is constructed by its context's factory")
}

data class NonDependency(override val typeName: TypeName) : Dependency {
    override val prefix
        get() = error("This dependency should not be used: $typeName")

    override val accessReference: String
        get() = error("This dependency should not be used: $typeName")

    override val requiresCommandDependencies = false
}
