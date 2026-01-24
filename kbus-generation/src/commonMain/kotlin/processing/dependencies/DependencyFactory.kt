package com.jimbroze.kbus.generation.processing.dependencies

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSValueParameter
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.reflect.KClass
import kotlinx.datetime.Clock

interface Dependencies {
    val topLevelDependencies: List<Dependency>
    val allDependencies: Set<DependencyWithChildren>
}

class DependencyFactory(
    private val kbusBusPackageName: String,
    @Suppress("unused") private val logger: KSPLogger,
) {

    fun generateChildDependencies(
        classType: KSType,
        classDeclaration: KSClassDeclaration,
        commandDependenciesProps: CommandDependencyProperties,
    ): Dependencies {
        if (classType.declaration != classDeclaration)
            error("Provided class type and declaration do not match")

        val dependencies = MutableDependencies()

        for (childParameter in classDeclaration.primaryConstructor?.parameters.orEmpty()) {
            val childType = resolveConstructorParameterType(childParameter, classType)
            dependencies.add(createNewDependency(commandDependenciesProps, childType))
        }

        return dependencies
    }

    fun generateDependencyWithChildren(
        type: KSType,
        commandDependenciesProps: CommandDependencyProperties,
        dependencyTypeOverride: DependencyType? = null,
    ): Dependencies {
        val dependencies = MutableDependencies()
        dependencies.add(
            createNewDependency(commandDependenciesProps, type, dependencyTypeOverride)
        )

        return dependencies
    }

    fun nameForDependency(declaration: KSDeclaration): String {
        return declaration.simpleName.asString().replaceFirstChar { it.lowercase() }
    }

    private fun mustBeRoot(parameter: KSDeclaration): Boolean {
        return parameter.packageName.asString() == kbusBusPackageName
    }

    private fun cannotBeDependency(
        parameter: KSDeclaration,
        commandDependencyProperties: CommandDependencyProperties,
    ): Boolean {
        val nonDependencyPrefixes = setOf("kotlin", "kotlinx")
        val canBeDependency = setOf<KClass<out Any>>(Clock::class)

        val disallowedByPackage =
            nonDependencyPrefixes.any { prefix ->
                parameter.packageName.asString().startsWith(prefix)
            } && canBeDependency.none { parameter.qualifiedName!!.asString() == it.qualifiedName }

        return commandDependencyProperties.contains(parameter) || disallowedByPackage
    }

    private fun createNewDependency(
        commandDependenciesProps: CommandDependencyProperties,
        type: KSType,
        dependencyTypeOverride: DependencyType? = null,
    ): NewDependencyWithChildren {
        val parameter = type.declaration

        val cannotBeDependency = cannotBeDependency(parameter, commandDependenciesProps)

        val children =
            if (shouldFindChildren(!cannotBeDependency, parameter))
                getNewChildren(type, parameter, commandDependenciesProps)
            else null

        val cannotBeAutoloaded =
            children == null ||
                children.topLevelChildren.isEmpty() ||
                children.parentCannotBeAutoloaded

        val isCommandDependency = commandDependenciesProps.contains(type.declaration)
        val requiresCommandDependencies =
            isCommandDependency || children?.requireCommandDependencies == true

        val metadata =
            if (isCommandDependency) {
                CommandDependency(type)
            } else if (cannotBeDependency) {
                NonDependency(type)
            } else if (dependencyTypeOverride != null) {
                Dependency.fromDependencyType(
                    dependencyTypeOverride,
                    type,
                    requiresCommandDependencies,
                )
            } else if (requiresCommandDependencies) {
                FunctionalDependency(type, true)
            } else {
                PropertyDependency(type)
            }

        val newDependency =
            DependencyWithChildren(
                metadata,
                children?.topLevelChildren ?: emptyList(),
                cannotBeAutoloaded,
            )

        return NewDependencyWithChildren(
            newDependency,
            children?.allDependencies ?: emptySet(),
            parentCannotBeAutoloaded(parameter, isCommandDependency, cannotBeDependency),
            requiresCommandDependencies,
        )
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

    @OptIn(ExperimentalContracts::class)
    private fun shouldFindChildren(
        isPossibleDependency: Boolean,
        parameter: KSDeclaration,
    ): Boolean {
        contract { returns(true) implies (parameter is KSClassDeclaration) }

        return parameter is KSClassDeclaration && isPossibleDependency && !mustBeRoot(parameter)
    }

    private fun getNewChildren(
        parentType: KSType,
        parentClass: KSClassDeclaration,
        commandDependenciesProps: CommandDependencyProperties,
    ): ChildrenDependencies {
        if (parentType.declaration != parentClass)
            error("Provided parent type and declaration do not match")

        val topLevelDependencies = mutableListOf<Dependency>()
        val allDependencies = mutableSetOf<DependencyWithChildren>()
        var parentCannotBeAutoloaded = false
        var childrenRequireCommandDependencies = false

        for (childParameter in parentClass.primaryConstructor?.parameters.orEmpty()) {
            val childType = resolveConstructorParameterType(childParameter, parentType)
            val childWithGrandchildren = createNewDependency(commandDependenciesProps, childType)

            topLevelDependencies.add(childWithGrandchildren.newDependency.metadata)
            allDependencies.addAll(childWithGrandchildren.getAll())

            if (childWithGrandchildren.parentOfNewDependencyCannotBeAutoloaded) {
                parentCannotBeAutoloaded = true
            }
            if (childWithGrandchildren.requireCommandDependencies) {
                childrenRequireCommandDependencies = true
            }
        }

        return ChildrenDependencies(
            topLevelDependencies,
            allDependencies,
            parentCannotBeAutoloaded,
            childrenRequireCommandDependencies,
        )
    }

    fun resolveConstructorParameterType(parameter: KSValueParameter, parentType: KSType): KSType {
        val parameterTypeNoTypeArgs = parameter.type.resolve()

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
        val requireCommandDependencies: Boolean,
    ) {
        fun getAll(): Set<DependencyWithChildren> {
            return setOf(newDependency) + allChildren
        }
    }

    private data class ChildrenDependencies(
        val topLevelChildren: List<Dependency>,
        val allDependencies: Set<DependencyWithChildren>,
        val parentCannotBeAutoloaded: Boolean,
        val requireCommandDependencies: Boolean,
    )
}
