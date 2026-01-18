package com.jimbroze.kbus.generation.processing.dependencies

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
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
        type: KSType,
        commandDependenciesProps: CommandDependencyProperties,
    ): Dependencies {
        val parameter = type.declaration as KSClassDeclaration

        val dependencies = MutableDependencies()

        for (childParameter in parameter.primaryConstructor?.parameters.orEmpty()) {
            dependencies.add(
                createNewDependency(commandDependenciesProps, type = childParameter.type.resolve())
            )
        }

        return dependencies
    }

    fun generateDependencyWithChildren(
        type: KSType,
        commandDependenciesProps: CommandDependencyProperties,
    ): Dependencies {
        val dependencies = MutableDependencies()
        dependencies.add(createNewDependency(commandDependenciesProps, type = type))

        return dependencies
    }

    fun nameForDependency(handlerClass: KSDeclaration): String {
        return handlerClass.simpleName.asString().replaceFirstChar { it.lowercase() }
    }

    private fun mustBeRoot(parameter: KSDeclaration): Boolean {
        return parameter.packageName.asString() == kbusBusPackageName
    }

    private fun cannotBeDependency(
        parameter: KSDeclaration,
        commandDependencyProperties: CommandDependencyProperties,
    ): Boolean {
        val nonDependencyPackages = setOf("kotlin", "kotlinx.datetime")
        val canBeDependency = setOf<KClass<out Any>>(Clock::class)

        val disallowedByPackage =
            nonDependencyPackages.contains(parameter.packageName.asString()) &&
                canBeDependency.none { parameter.qualifiedName!!.asString() == it.qualifiedName }

        return commandDependencyProperties.contains(parameter) || disallowedByPackage
    }

    private fun createNewDependency(
        commandDependenciesProps: CommandDependencyProperties,
        type: KSType,
    ): NewDependencyWithChildren {
        val parameter = type.declaration

        val cannotBeDependency = cannotBeDependency(parameter, commandDependenciesProps)

        val children =
            if (shouldFindChildren(!cannotBeDependency, parameter))
                getNewChildren(parameter, commandDependenciesProps)
            else null

        val isRoot =
            children == null || children.topLevelChildren.isEmpty() || children.parentIsRoot

        val isCommandDependency = commandDependenciesProps.contains(type.declaration)
        val requiresCommandDependencies =
            isCommandDependency || children?.requireCommandDependencies == true

        val parentIsRoot =
            !isCommandDependency &&
                (cannotBeDependency || parameter !is KSClassDeclaration || mustBeRoot(parameter))

        val metadata =
            if (isCommandDependency) {
                CommandDependency(type)
            } else if (cannotBeDependency) {
                NonDependency(type)
            } else if (requiresCommandDependencies) {
                FunctionalDependency(type, true)
            } else {
                PropertyDependency(type)
            }

        val newDependency =
            DependencyWithChildren(metadata, children?.topLevelChildren ?: emptyList(), isRoot)

        return NewDependencyWithChildren(
            newDependency,
            children?.allDependencies ?: emptySet(),
            parentIsRoot,
            requiresCommandDependencies,
        )
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
        parentClass: KSClassDeclaration,
        commandDependenciesProps: CommandDependencyProperties,
    ): ChildrenDependencies {
        val topLevelDependencies = mutableListOf<Dependency>()
        val allDependencies = mutableSetOf<DependencyWithChildren>()
        var parentIsRoot = false
        var childrenRequireCommandDependencies = false

        for (childParameter in parentClass.primaryConstructor?.parameters.orEmpty()) {
            // TODO if in allDependencies, get metadata from there and don't recreate
            val childWithChildren =
                createNewDependency(commandDependenciesProps, type = childParameter.type.resolve())

            topLevelDependencies.add(childWithChildren.newDependency.metadata)
            allDependencies.addAll(childWithChildren.getAll())

            if (childWithChildren.parentIsRoot) {
                parentIsRoot = true
            }
            if (childWithChildren.requireCommandDependencies) {
                childrenRequireCommandDependencies = true
            }
        }

        return ChildrenDependencies(
            topLevelDependencies,
            allDependencies,
            parentIsRoot,
            childrenRequireCommandDependencies,
        )
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
        val parentIsRoot: Boolean,
        val requireCommandDependencies: Boolean,
    ) {
        fun getAll(): Set<DependencyWithChildren> {
            return setOf(newDependency) + allChildren
        }
    }

    private data class ChildrenDependencies(
        val topLevelChildren: List<Dependency>,
        val allDependencies: Set<DependencyWithChildren>,
        val parentIsRoot: Boolean,
        val requireCommandDependencies: Boolean,
    )
}
