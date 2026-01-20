package com.jimbroze.kbus.generation.processing.dependencies

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.jimbroze.kbus.core.uow.CommandDependencies

class CommandDependencyProperties(properties: Set<KSType>) {
    val propertyNames: Set<String> =
        properties.mapNotNull { it.declaration.qualifiedName?.asString() }.toSet()

    companion object {
        fun fromResolver(resolver: Resolver): CommandDependencyProperties {
            val commandDependenciesClass =
                resolver.getClassDeclarationByName(CommandDependencies::class.qualifiedName!!)!!

            val props =
                commandDependenciesClass.getAllProperties().map { it.type.resolve() }.toMutableSet()

            return CommandDependencyProperties(
                props + commandDependenciesClass.asStarProjectedType()
            )
        }
    }

    fun contains(prop: KSDeclaration): Boolean {
        return this.propertyNames.contains(prop.qualifiedName?.asString())
    }
}
