@file:OptIn(ExperimentalTime::class)

package com.jimbroze.kbus.generation.processing.dependencies

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSValueParameter
import com.jimbroze.kbus.api.annotations.index.RequiredDependencies
import com.jimbroze.kbus.application.messages.command.ContextCommands
import com.squareup.kotlinpoet.ksp.toTypeName
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

interface Dependencies {
    val topLevelDependencies: List<Dependency>
    val allDependencies: Set<DependencyWithChildren>
}

class DependencyFactory(@Suppress("unused") private val logger: KSPLogger) {
    fun generateChildDependencies(
        classType: KSType,
        classDeclaration: KSClassDeclaration,
        commandDependenciesProps: CommandDependencyProperties,
    ): Dependencies {
        require(classType.declaration == classDeclaration) {
            "Provided class type and declaration do not match"
        }

        val dependencies = MutableDependencies()

        for (childParameter in classDeclaration.primaryConstructor?.parameters.orEmpty()) {
            val childType = childParameter.resolveTypeUsingParent(classType)
            dependencies.add(createNewDependency(commandDependenciesProps, childType))
        }

        return dependencies
    }

    private fun createNewDependency(
        commandDependenciesProps: CommandDependencyProperties,
        type: KSType,
    ): NewDependencyWithChildren {
        val parameter = type.declaration

        val cannotBeDependency = cannotBeDependency(parameter, commandDependenciesProps)
        val isExternalDependency = isExternalDependency(parameter)

        val children =
            if (shouldFindChildren(!cannotBeDependency, isExternalDependency, parameter))
                getNewChildren(type, parameter, commandDependenciesProps)
            else null

        val isContextCommands = isContextCommandsInterface(parameter)
        val invocationScopedProperty = commandDependenciesProps.propertyFor(type.declaration)
        val isCommandScoped = invocationScopedProperty != null || isContextCommands
        val ownRequiredDependencies =
            when {
                isContextCommands -> RequiredDependencies.COMMAND
                invocationScopedProperty != null -> invocationScopedProperty.requiredDependencies
                else -> RequiredDependencies.NONE
            }
        val requiredDependencies =
            ownRequiredDependencies.widestWith(
                children?.requiredDependencies ?: RequiredDependencies.NONE
            )

        val metadata =
            createDependencyMetadata(
                type,
                invocationScopedProperty,
                isContextCommands,
                cannotBeDependency,
                requiredDependencies,
            )

        return NewDependencyWithChildren(
            DependencyWithChildren(
                metadata,
                children?.topLevelChildren ?: emptyList(),
                cannotBeAutoloaded(children, isExternalDependency),
            ),
            children?.allDependencies ?: emptySet(),
            parentCannotBeAutoloaded(parameter, isCommandScoped, cannotBeDependency),
            requiredDependencies,
        )
    }

    private fun getNewChildren(
        parentType: KSType,
        parentClass: KSClassDeclaration,
        commandDependenciesProps: CommandDependencyProperties,
    ): ChildrenDependencies {
        require(parentType.declaration == parentClass) {
            "Provided parent type and declaration do not match"
        }

        val topLevelDependencies = mutableListOf<Dependency>()
        val allDependencies = mutableSetOf<DependencyWithChildren>()
        var parentCannotBeAutoloaded = false
        var childrenRequiredDependencies = RequiredDependencies.NONE

        for (childParameter in parentClass.primaryConstructor?.parameters.orEmpty()) {
            val childType = childParameter.resolveTypeUsingParent(parentType)
            val childWithGrandchildren = createNewDependency(commandDependenciesProps, childType)

            topLevelDependencies.add(childWithGrandchildren.newDependency.metadata)
            allDependencies.addAll(childWithGrandchildren.getAll())

            if (childWithGrandchildren.parentOfNewDependencyCannotBeAutoloaded) {
                parentCannotBeAutoloaded = true
            }
            childrenRequiredDependencies =
                childrenRequiredDependencies.widestWith(childWithGrandchildren.requiredDependencies)
        }

        return ChildrenDependencies(
            topLevelDependencies,
            allDependencies,
            parentCannotBeAutoloaded,
            childrenRequiredDependencies,
        )
    }

    @OptIn(ExperimentalContracts::class)
    private fun shouldFindChildren(
        isPossibleDependency: Boolean,
        isExternalDependency: Boolean,
        parameter: KSDeclaration,
    ): Boolean {
        contract { returns(true) implies (parameter is KSClassDeclaration) }

        return isPossibleDependency && !isExternalDependency && parameter is KSClassDeclaration
    }
}

fun KSValueParameter.resolveTypeUsingParent(parentType: KSType): KSType {
    val parameterTypeNoTypeArgs = this.type.resolve()

    val declaration = parameterTypeNoTypeArgs.declaration

    if (declaration is KSTypeParameter) {
        val index =
            parentType.declaration.typeParameters.indexOfFirst {
                it.name.asString() == declaration.name.asString()
            }

        if (index != -1 && index < parentType.arguments.size) {
            val resolvedArgument = parentType.arguments[index].type?.resolve()
            if (resolvedArgument != null) {
                return resolvedArgument
            }
        }
    }

    return parameterTypeNoTypeArgs
}

private fun createDependencyMetadata(
    type: KSType,
    invocationScopedProperty: CommandDependencyProperties.InvocationScopedProperty?,
    isContextCommands: Boolean,
    cannotBeDependency: Boolean,
    requiredDependencies: RequiredDependencies,
): Dependency {
    return if (isContextCommands) {
        ContextCommandsDependency(type.toTypeName())
    } else if (invocationScopedProperty != null) {
        CommandDependency(
            type.toTypeName(),
            invocationScopedProperty.propertyName,
            invocationScopedProperty.requiredDependencies,
        )
    } else if (cannotBeDependency) {
        NonDependency(type.toTypeName())
    } else if (requiredDependencies != RequiredDependencies.NONE) {
        FunctionalDependency(type.toTypeName(), requiredDependencies)
    } else {
        PropertyDependency(type.toTypeName())
    }
}

private fun cannotBeDependency(
    parameter: KSDeclaration,
    commandDependencyProperties: CommandDependencyProperties,
): Boolean {
    val nonDependencyPrefixes = setOf("kotlin", "kotlinx")
    val canBeDependency = setOf(Clock::class.qualifiedName!!, "kotlinx.datetime.Clock")

    val disallowedByPackage =
        nonDependencyPrefixes.any { prefix ->
            parameter.packageName.asString().startsWith(prefix)
        } && canBeDependency.none { parameter.qualifiedName!!.asString() == it }

    return commandDependencyProperties.contains(parameter) ||
        isContextCommandsInterface(parameter) ||
        disallowedByPackage
}

/**
 * A generated per-context command executor. Recognised structurally rather than by name, so a
 * module can inject one another module generated without knowing what it is called.
 */
private fun isContextCommandsInterface(declaration: KSDeclaration): Boolean =
    declaration is KSClassDeclaration &&
        declaration.getAllSuperTypes().any {
            it.declaration.qualifiedName?.asString() == ContextCommands::class.qualifiedName
        }

private fun cannotBeAutoloaded(
    childrenOfDependency: ChildrenDependencies?,
    dependencyIsExternal: Boolean,
): Boolean {
    return dependencyIsExternal ||
        childrenOfDependency == null ||
        childrenOfDependency.parentCannotBeAutoloaded ||
        childrenOfDependency.topLevelChildren.isEmpty()
}

private fun parentCannotBeAutoloaded(
    childParameter: KSDeclaration,
    childIsCommandDependency: Boolean,
    childCannotBeDependency: Boolean,
): Boolean {
    if (childIsCommandDependency) return false

    val isSupportedConstructorParamTypeForAutoloading =
        when (childParameter) {
            is KSClassDeclaration,
            is KSTypeAlias -> true
            else -> false
        }

    return childCannotBeDependency || !isSupportedConstructorParamTypeForAutoloading
}

private fun isExternalDependency(declaration: KSDeclaration): Boolean {
    val isLibrary = declaration.containingFile == null
    val pkg = declaration.packageName.asString()

    val isBuiltInLibrary = pkg.startsWith("kotlin") || pkg.startsWith("java")

    return isLibrary && !isBuiltInLibrary
}

private class MutableDependencies : Dependencies {
    override val topLevelDependencies = mutableListOf<Dependency>()

    override val allDependencies = mutableSetOf<DependencyWithChildren>()

    fun add(dependencies: NewDependencyWithChildren) {
        topLevelDependencies.add(dependencies.newDependency.metadata)
        allDependencies.addAll(dependencies.getAll())
    }
}

private data class NewDependencyWithChildren(
    val newDependency: DependencyWithChildren,
    val allChildren: Set<DependencyWithChildren>,
    val parentOfNewDependencyCannotBeAutoloaded: Boolean,
    val requiredDependencies: RequiredDependencies,
) {
    fun getAll(): Set<DependencyWithChildren> {
        return setOf(newDependency) + allChildren
    }
}

private data class ChildrenDependencies(
    val topLevelChildren: List<Dependency>,
    val allDependencies: Set<DependencyWithChildren>,
    val parentCannotBeAutoloaded: Boolean,
    val requiredDependencies: RequiredDependencies,
)
