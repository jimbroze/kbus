package com.jimbroze.kbus.generation.processing.dependencies

import com.jimbroze.kbus.contracts.annotations.index.RequiredDependencies
import com.jimbroze.kbus.core.messages.HandlerDependencies
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.squareup.kotlinpoet.TypeName
import kotlin.reflect.KClass

val RequiredDependencies.parameterName: String
    get() =
        when (this) {
            RequiredDependencies.NONE ->
                error("Nothing invocation-scoped is supplied to this accessor")
            RequiredDependencies.HANDLER_ONLY -> "handlerDependencies"
            RequiredDependencies.COMMAND -> "commandDependencies"
        }

val RequiredDependencies.parameterType: KClass<*>
    get() =
        when (this) {
            RequiredDependencies.NONE ->
                error("Nothing invocation-scoped is supplied to this accessor")
            RequiredDependencies.HANDLER_ONLY -> HandlerDependencies::class
            RequiredDependencies.COMMAND -> CommandDependencies::class
        }

/** Whichever of the two can satisfy both. */
fun RequiredDependencies.widestWith(other: RequiredDependencies): RequiredDependencies =
    if (other.ordinal > this.ordinal) other else this

sealed interface Dependency {
    val requiredDependencies: RequiredDependencies

    val name: String
        get() = NameGenerator.getNameForType(typeName)

    val signature: String
        get() = typeName.toString()

    val typeName: TypeName

    val prefix: String
        get() = ""

    /**
     * How this is referenced from inside an accessor supplied with [enclosingDependencies] — which
     * is the accessor's own parameter, not this dependency's narrower requirement.
     */
    fun accessReferenceIn(enclosingDependencies: RequiredDependencies): String =
        "${prefixIn(enclosingDependencies)}$name"

    fun prefixIn(
        @Suppress("UNUSED_PARAMETER") enclosingDependencies: RequiredDependencies
    ): String = prefix

    fun hasConflictingNameWith(other: Dependency): Boolean {
        return this.name == other.name && this != other
    }
}

data class PropertyDependency(override val typeName: TypeName) : Dependency {
    override val requiredDependencies = RequiredDependencies.NONE
}

data class FunctionalDependency(
    override val typeName: TypeName,
    override val requiredDependencies: RequiredDependencies,
) : Dependency {
    data class DependencyConstructorParameters(val name: String, val typeRef: KClass<*>)

    val functionParameters: List<DependencyConstructorParameters>
        get() =
            if (requiredDependencies == RequiredDependencies.NONE) emptyList()
            else
                listOf(
                    DependencyConstructorParameters(
                        requiredDependencies.parameterName,
                        requiredDependencies.parameterType,
                    )
                )

    override fun accessReferenceIn(enclosingDependencies: RequiredDependencies): String {
        val constructorArgNames =
            if (requiredDependencies == RequiredDependencies.NONE) ""
            else enclosingDependencies.parameterName
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
    override val requiredDependencies: RequiredDependencies,
) : CommandScopedDependency {
    override fun prefixIn(enclosingDependencies: RequiredDependencies) =
        if (isWholeDependencyObject) "" else "${enclosingDependencies.parameterName}."

    override fun accessReferenceIn(enclosingDependencies: RequiredDependencies): String =
        if (isWholeDependencyObject) enclosingDependencies.parameterName
        else "${prefixIn(enclosingDependencies)}$name"

    private val isWholeDependencyObject
        get() = name == WHOLE_OBJECT

    companion object {
        const val WHOLE_OBJECT = "commandDependencies"
    }
}

/**
 * A handler's parameter of a generated per-context command executor interface. It is handed to the
 * accessor building the handler rather than read from anything, because only its context can supply
 * a value bound to that context.
 */
data class ContextCommandsDependency(override val typeName: TypeName) : CommandScopedDependency {
    override val requiredDependencies = RequiredDependencies.COMMAND

    override val prefix
        get() = ""

    override fun accessReferenceIn(enclosingDependencies: RequiredDependencies): String =
        error("A handler's context commands are named by the context generating its accessor")
}

data class NonDependency(override val typeName: TypeName) : Dependency {
    override val prefix
        get() = error("This dependency should not be used: $typeName")

    override fun accessReferenceIn(enclosingDependencies: RequiredDependencies): String =
        error("This dependency should not be used: $typeName")

    override val requiredDependencies = RequiredDependencies.NONE
}
