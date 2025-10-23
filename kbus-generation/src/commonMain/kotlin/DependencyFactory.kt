package com.jimbroze.kbus.generation

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.jimbroze.kbus.core.CommandDependencies
import kotlin.String
import kotlin.collections.orEmpty
import kotlin.reflect.KClass
import kotlinx.datetime.Clock

class CommandDependencyProperties(
    private val dependencyFactory: DependencyFactory,
    private val properties: Set<KSType>,
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

    fun asDependencies(): Set<NestedDependency> {
        return properties
            .flatMap {
                dependencyFactory.generateFromType(
                    it,
                    includeNested = false,
                    commandDependenciesProps =
                        CommandDependencyProperties(this.dependencyFactory, emptySet()),
                )
            }
            .toSet()
    }
}

// TODO rename isRoot
// TODO rename class to NestedDependency and do something with current NestedDependency
// TODO reduce levels of recursion if not getting all children?
// TODO create ABC to split noDependency
class DependencyWithChildren(
    private val dependency: NestedDependency,
    private val children: AllChildrenDependencies,
    private val removeDependency: Boolean = false,
) {
    companion object {
        fun noDependency(dependency: NestedDependency) =
            DependencyWithChildren(
                dependency,
                AllChildrenDependencies.noChildren(),
                removeDependency = true,
            )
    }

    fun getAll(includeNested: Boolean): List<NestedDependency> {
        val dependencies = mutableListOf<NestedDependency>()

        if (!removeDependency) {
            dependencies.add(dependency)
        }
        //        dependency?.let { dependencies.add(it) }

        if (includeNested) {
            dependencies.addAll(children.dependencies)
        }

        return dependencies
    }

    fun requiresCommandDependencies(): Boolean {
        return dependency.isCommandDependency || children.requireCommandDependencies
    }

    fun getName(): String? {
        return dependency.name.takeIf {
            !removeDependency || grandParentIsNonRootButHasMissingChild()
        }
    }

    fun grandParentIsNonRootButHasMissingChild(): Boolean =
        removeDependency && requiresCommandDependencies()

    fun grandParentIsRoot(): Boolean = removeDependency && !requiresCommandDependencies()
}

data class AllChildrenDependencies(
    val dependencies: List<NestedDependency>,
    val requireCommandDependencies: Boolean,
    val directChildrenNames: List<String>,
    private val parentIsRoot: Boolean,
) {
    companion object Companion {
        fun noChildren() = AllChildrenDependencies(mutableListOf(), false, emptyList(), true)
    }

    fun parentIsRoot(): Boolean = parentIsRoot || dependencies.isEmpty()
}

@Suppress("unused")
class DependencyFactory(private val kbusBusPackageName: String, private val logger: KSPLogger) {
    // TODO rename type. Type of what?
    fun generateFromType(
        type: KSType,
        includeNested: Boolean,
        commandDependenciesProps: CommandDependencyProperties,
        customName: String? = null,
        typeArgs: List<KSTypeArgument> = emptyList(),
    ): List<NestedDependency> {
        return createDependency(commandDependenciesProps, type, customName, typeArgs)
            .getAll(includeNested)
            .distinct()
    }

    // TODO combine with below funcs?? multiple places that decide if root
    private fun createDependency(
        commandDependenciesProps: CommandDependencyProperties,
        parameterType: KSType,
        customName: String? = null,
        typeArgs: List<KSTypeArgument> = emptyList(),
    ): DependencyWithChildren {
        val parameter = parameterType.declaration

        val cannotBeDependency = cannotBeDependency(parameter, commandDependenciesProps)

        val children =
            if (parameter !is KSClassDeclaration || cannotBeDependency || mustBeRoot(parameter)) {
                AllChildrenDependencies.noChildren()
            } else {
                nestedDependencies(commandDependenciesProps, parameter)
            }

        // TODO Move to DependencyWithChildren???
        val dependency =
            NestedDependency.fromDependency(
                Dependency.withCustomName(
                    parameter,
                    typeArgs,
                    customName = customName,
                    nullability = parameterType.nullability,
                ),
                children.parentIsRoot(),
                commandDependenciesProps.contains(parameter) || children.requireCommandDependencies,
                children.directChildrenNames,
            )

        return DependencyWithChildren(dependency, children, cannotBeDependency)
    }

    // TODO decides if root (has nested). Combine with below??? And above!! (cannoteBeDependency)
    private fun nestedDependencies(
        commandDependenciesProps: CommandDependencyProperties,
        parent: KSClassDeclaration,
    ): AllChildrenDependencies {
        // TODO prevent calculating nested if extractNested is false? Probably can't do this? At
        // least prevent recursion?
        val allDependencies = mutableListOf<NestedDependency>()
        var parentIsRoot = false
        var childrenRequireCommandDependencies = false
        val childNames = mutableListOf<String>()

        for (childParameter in parent.primaryConstructor?.parameters.orEmpty()) {
            val childWithChildren =
                createDependency(
                    commandDependenciesProps,
                    childParameter.type.resolve(),
                    typeArgs = childParameter.type.element?.typeArguments.orEmpty(),
                )

            childWithChildren.getName()?.let { childNames.add(it) }
            allDependencies.addAll(childWithChildren.getAll(includeNested = true))

            if (childWithChildren.grandParentIsRoot()) {
                parentIsRoot = true
            }
            if (childWithChildren.requiresCommandDependencies()) {
                childrenRequireCommandDependencies = true
            }
        }

        //        val allDependencies =
        //            parent.primaryConstructor?.parameters.orEmpty().map { childParameter ->
        //                createDependency(
        //                    commandDependenciesProps,
        //                    childParameter.type.resolve(),
        //                    typeArgs = childParameter.type.element?.typeArguments.orEmpty(),
        //                )
        //            }
        //        val childrenRequireCommandDependencies =
        //            allDependencies.any { it.requiresCommandDependencies() }
        //        val childNames = allDependencies.mapNotNull { it.getName() }

        return AllChildrenDependencies(
            allDependencies,
            childrenRequireCommandDependencies,
            childNames,
            parentIsRoot,
        )
    }

    private fun mustBeRoot(parameter: KSClassDeclaration): Boolean {
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
}
