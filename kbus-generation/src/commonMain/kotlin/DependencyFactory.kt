package com.jimbroze.kbus.generation

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.jimbroze.kbus.core.CommandDependencies
import kotlin.String
import kotlin.collections.orEmpty
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.reflect.KClass
import kotlinx.datetime.Clock

class CommandDependencyProperties(
    private val dependencyFactory: DependencyFactory,
    properties: Set<KSType>,
) {
    val propertyNames: Set<String> =
        properties.mapNotNull { it.declaration.qualifiedName?.asString() }.toSet()

    companion object {
        fun fromResolver(
            resolver: Resolver,
            dependencyFactory: DependencyFactory,
        ): CommandDependencyProperties {
            val commandDependenciesClass =
                resolver.getClassDeclarationByName(CommandDependencies::class.qualifiedName!!)!!

            val props =
                commandDependenciesClass.getAllProperties().map { it.type.resolve() }.toMutableSet()

            return CommandDependencyProperties(
                dependencyFactory,
                props + commandDependenciesClass.asStarProjectedType(),
            )
        }
    }

    fun contains(prop: KSDeclaration): Boolean {
        return this.propertyNames.contains(prop.qualifiedName?.asString())
    }
}

interface Dependencies {
    val topLevelDependencies: List<DependencyMetadata>
    val allDependencies: Set<DependencyNested>
}

@Suppress("unused")
class DependencyFactory(private val kbusBusPackageName: String, private val logger: KSPLogger) {
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

    fun nameForDependency(handlerClass: KSDeclaration): String {
        return handlerClass.simpleName.asString().replaceFirstChar { it.lowercase() }
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
                CommandDependencyMetadata(type)
            } else if (cannotBeDependency) {
                NonDependencyMetadata(type)
            } else if (requiresCommandDependencies) {
                FunctionalDependencyMetadata(type, true)
            } else {
                PropertyDependencyMetadata(type)
            }

        val newDependency =
            DependencyNested(metadata, children?.topLevelChildren ?: emptyList(), isRoot)

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
        val topLevelDependencies = mutableListOf<DependencyMetadata>()
        val allDependencies = mutableSetOf<DependencyNested>()
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
        override val topLevelDependencies = mutableListOf<DependencyMetadata>()
        override val allDependencies = mutableSetOf<DependencyNested>()

        fun add(dependencies: NewDependencyWithChildren) {
            topLevelDependencies.add(dependencies.newDependency.metadata)
            allDependencies.addAll(dependencies.getAll())
        }
    }

    private data class NewDependencyWithChildren(
        val newDependency: DependencyNested,
        val allChildren: Set<DependencyNested>,
        val parentIsRoot: Boolean,
        val requireCommandDependencies: Boolean,
    ) {
        fun getAll(): Set<DependencyNested> {
            return setOf(newDependency) + allChildren
        }
    }

    private data class ChildrenDependencies(
        val topLevelChildren: List<DependencyMetadata>,
        val allDependencies: Set<DependencyNested>,
        val parentIsRoot: Boolean,
        val requireCommandDependencies: Boolean,
    )
}

// TODO add constructorArgs method and make handlerData implement this?
data class DependencyNested(
    val metadata: DependencyMetadata,
    override val topLevelDependencies: List<DependencyMetadata>,
    val isRoot: Boolean,
) : HasChildren {

    //    override fun equals(other: Any?): Boolean {
    //        return other is DependencyNested && metadata == other.metadata
    //    }

    //    override fun hashCode(): Int {
    //        var result = isRoot.hashCode()
    //        result = 31 * result + metadata.hashCode()
    //        result = 31 * result + topLevelDependencies.hashCode()
    //        return result
    //    }
    //    override fun hashCode(): Int {
    //        return metadata.hashCode()
    //    }

    //    val isRoot: Boolean // Do we need non-root deps? What for?
    //    val requiresCommandDependencies: Boolean // Do we need this? Is it just for creation?
    //    companion object {
    //        fun create(
    //            typeRef: KSType,
    //            isRoot: Boolean,
    //            requiresCommandDependencies: Boolean,
    //            commandDependenciesProps: CommandDependencyProperties,
    //            constructorDependencies: List<DependencyMetadata>,
    //        ): DependencyNested {
    //            val metadata =
    //                if (commandDependenciesProps.contains(typeRef.declaration)) {
    //                    CommandDependencyMetadata(typeRef)
    //                } else if (requiresCommandDependencies) {
    //                    FunctionalDependencyMetadata(typeRef, requiresCommandDependencies)
    //                } else {
    //                    PropertyDependencyMetadata(typeRef)
    //                }
    //
    //            return DependencyNested(metadata, constructorDependencies, isRoot)
    //        }
    //    }
}
