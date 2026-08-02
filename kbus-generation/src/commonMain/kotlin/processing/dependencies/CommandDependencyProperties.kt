package com.jimbroze.kbus.generation.processing.dependencies

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSDeclaration
import com.jimbroze.kbus.core.messages.command.CommandDependencies

/**
 * What a handler can ask for by declaring a constructor parameter of a [CommandDependencies]
 * property's type, and the property name each is read from. The name is carried rather than derived
 * from the type, because a property whose name is not its own decapitalised type name would
 * otherwise generate a reference to something that does not exist.
 */
class CommandDependencyProperties(private val propertyNamesByType: Map<String, String>) {
    companion object {
        fun fromResolver(resolver: Resolver): CommandDependencyProperties {
            val commandDependenciesClass =
                resolver.getClassDeclarationByName(CommandDependencies::class.qualifiedName!!)!!

            val propertyNamesByType =
                commandDependenciesClass
                    .getAllProperties()
                    .mapNotNull { property ->
                        property.type.resolve().declaration.qualifiedName?.asString()?.let {
                            it to property.simpleName.asString()
                        }
                    }
                    .toMap()

            return CommandDependencyProperties(
                propertyNamesByType +
                    (CommandDependencies::class.qualifiedName!! to CommandDependency.WHOLE_OBJECT)
            )
        }
    }

    fun contains(prop: KSDeclaration): Boolean = propertyNameFor(prop) != null

    /** Null when [prop] is not something a command's dependencies can supply. */
    fun propertyNameFor(prop: KSDeclaration): String? =
        propertyNamesByType[prop.qualifiedName?.asString()]
}
