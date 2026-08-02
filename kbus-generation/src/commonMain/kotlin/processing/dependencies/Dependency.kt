package com.jimbroze.kbus.generation.processing.dependencies

import com.jimbroze.kbus.contracts.annotations.index.DependencyBundle
import com.jimbroze.kbus.core.messages.HandlerDependencies
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.squareup.kotlinpoet.TypeName
import kotlin.reflect.KClass

/** The parameter a generated accessor takes to supply [bundle], and the type it is declared as. */
val DependencyBundle.parameterName: String
    get() =
        when (this) {
            DependencyBundle.NONE -> error("Nothing invocation-scoped is supplied to this accessor")
            DependencyBundle.HANDLER -> "handlerDependencies"
            DependencyBundle.COMMAND -> "commandDependencies"
        }

val DependencyBundle.parameterType: KClass<*>
    get() =
        when (this) {
            DependencyBundle.NONE -> error("Nothing invocation-scoped is supplied to this accessor")
            DependencyBundle.HANDLER -> HandlerDependencies::class
            DependencyBundle.COMMAND -> CommandDependencies::class
        }

/** Whichever of the two can satisfy both. */
fun DependencyBundle.widestWith(other: DependencyBundle): DependencyBundle =
    if (other.ordinal > this.ordinal) other else this

sealed interface Dependency {
    /** The narrowest bundle this can be built from — [DependencyBundle.NONE] if it needs none. */
    val requiredBundle: DependencyBundle

    val name: String
        get() = NameGenerator.getNameForType(typeName)

    val signature: String
        get() = typeName.toString()

    val typeName: TypeName

    val prefix: String
        get() = ""

    /**
     * How this is referenced from inside an accessor supplied with [enclosingBundle] — which is the
     * accessor's own parameter, not this dependency's narrower requirement.
     */
    fun accessReferenceIn(enclosingBundle: DependencyBundle): String =
        "${prefixIn(enclosingBundle)}$name"

    fun prefixIn(@Suppress("UNUSED_PARAMETER") enclosingBundle: DependencyBundle): String = prefix

    fun hasConflictingNameWith(other: Dependency): Boolean {
        return this.name == other.name && this != other
    }
}

data class PropertyDependency(override val typeName: TypeName) : Dependency {
    override val requiredBundle = DependencyBundle.NONE
}

data class FunctionalDependency(
    override val typeName: TypeName,
    override val requiredBundle: DependencyBundle,
) : Dependency {
    data class DependencyConstructorParameters(val name: String, val typeRef: KClass<*>)

    val functionParameters: List<DependencyConstructorParameters>
        get() =
            if (requiredBundle == DependencyBundle.NONE) emptyList()
            else
                listOf(
                    DependencyConstructorParameters(
                        requiredBundle.parameterName,
                        requiredBundle.parameterType,
                    )
                )

    /**
     * The enclosing accessor's parameter is passed straight through: it is always at least as wide
     * as this dependency's own requirement, so it satisfies it.
     */
    override fun accessReferenceIn(enclosingBundle: DependencyBundle): String {
        val constructorArgNames =
            if (requiredBundle == DependencyBundle.NONE) "" else enclosingBundle.parameterName
        return "$prefix$name($constructorArgNames)"
    }
}

/**
 * A dependency built from the command's own [CommandDependencies] rather than from the container,
 * so it never appears in the generated dependency interface or autoloader.
 */
sealed interface CommandScopedDependency : Dependency

/**
 * [name] is the [CommandDependencies] property this is read from, or [WHOLE_OBJECT] when the
 * handler asks for the object itself. It cannot be derived from the type: a property named other
 * than its own decapitalised type name would generate a reference to nothing.
 */
data class CommandDependency(
    override val typeName: TypeName,
    override val name: String,
    override val requiredBundle: DependencyBundle,
) : CommandScopedDependency {
    override fun prefixIn(enclosingBundle: DependencyBundle) =
        if (isWholeBundle) "" else "${enclosingBundle.parameterName}."

    override fun accessReferenceIn(enclosingBundle: DependencyBundle): String =
        if (isWholeBundle) enclosingBundle.parameterName else "${prefixIn(enclosingBundle)}$name"

    private val isWholeBundle
        get() = name == WHOLE_OBJECT

    companion object {
        const val WHOLE_OBJECT = "commandDependencies"
    }
}

/**
 * A handler's parameter of a generated per-context command executor interface. Its access
 * expression depends on the owning context of the handler being built, which only the factory
 * generating that handler knows, so it has none of its own.
 */
data class ContextCommandsDependency(override val typeName: TypeName) : CommandScopedDependency {
    override val requiredBundle = DependencyBundle.COMMAND

    override val prefix
        get() = error("A context command executor is constructed by its context's factory")

    override fun accessReferenceIn(enclosingBundle: DependencyBundle): String =
        error("A context command executor is constructed by its context's factory")
}

data class NonDependency(override val typeName: TypeName) : Dependency {
    override val prefix
        get() = error("This dependency should not be used: $typeName")

    override fun accessReferenceIn(enclosingBundle: DependencyBundle): String =
        error("This dependency should not be used: $typeName")

    override val requiredBundle = DependencyBundle.NONE
}
