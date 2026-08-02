package com.jimbroze.kbus.generation.processing.dependencies

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSDeclaration
import com.jimbroze.kbus.contracts.annotations.index.DependencyBundle
import com.jimbroze.kbus.core.messages.HandlerDependencies
import com.jimbroze.kbus.core.messages.command.CommandDependencies

/**
 * What a handler can ask for by declaring a constructor parameter of an invocation-scoped
 * property's type: the property name each is read from, and the narrowest bundle carrying it.
 *
 * The name is carried rather than derived from the type, because a property whose name is not its
 * own decapitalised type name would otherwise generate a reference to something that does not
 * exist. The bundle is what keeps a command-only dependency out of an event handler.
 */
class CommandDependencyProperties(
    private val propertiesByType: Map<String, InvocationScopedProperty>
) {
    data class InvocationScopedProperty(val propertyName: String, val bundle: DependencyBundle)

    companion object {
        fun fromResolver(resolver: Resolver): CommandDependencyProperties {
            val handlerPropertyTypes =
                propertyNamesOf(resolver, HandlerDependencies::class.qualifiedName!!).keys

            val propertiesByType =
                propertyNamesOf(resolver, CommandDependencies::class.qualifiedName!!).mapValues {
                    (type, propertyName) ->
                    InvocationScopedProperty(
                        propertyName,
                        if (type in handlerPropertyTypes) DependencyBundle.HANDLER
                        else DependencyBundle.COMMAND,
                    )
                }

            return CommandDependencyProperties(
                propertiesByType +
                    (HandlerDependencies::class.qualifiedName!! to
                        InvocationScopedProperty(
                            CommandDependency.WHOLE_OBJECT,
                            DependencyBundle.HANDLER,
                        )) +
                    (CommandDependencies::class.qualifiedName!! to
                        InvocationScopedProperty(
                            CommandDependency.WHOLE_OBJECT,
                            DependencyBundle.COMMAND,
                        ))
            )
        }

        private fun propertyNamesOf(resolver: Resolver, className: String): Map<String, String> =
            resolver
                .getClassDeclarationByName(className)!!
                .getAllProperties()
                .mapNotNull { property ->
                    property.type.resolve().declaration.qualifiedName?.asString()?.let {
                        it to property.simpleName.asString()
                    }
                }
                .toMap()
    }

    fun contains(prop: KSDeclaration): Boolean = propertyFor(prop) != null

    /** Null when [prop] is not something an invocation's dependencies can supply. */
    fun propertyFor(prop: KSDeclaration): InvocationScopedProperty? =
        propertiesByType[prop.qualifiedName?.asString()]
}
