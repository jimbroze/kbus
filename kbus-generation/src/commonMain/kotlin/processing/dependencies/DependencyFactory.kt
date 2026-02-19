package com.jimbroze.kbus.generation.processing.dependencies

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ksp.toTypeName
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
            val childType = childParameter.resolveTypeUsingParent(classType)
            dependencies.add(createNewDependency(commandDependenciesProps, childType))
        }

        return dependencies
    }

    fun generateDependencyWithChildren(
        type: KSType,
        commandDependenciesProps: CommandDependencyProperties,
        dependencyTypeOverride: DependencyOverrideType? = null,
    ): Dependencies {
        val dependencies = MutableDependencies()
        dependencies.add(
            createNewDependency(commandDependenciesProps, type, dependencyTypeOverride)
        )

        return dependencies
    }

    private fun createNewDependency(
        commandDependenciesProps: CommandDependencyProperties,
        type: KSType,
        dependencyTypeOverride: DependencyOverrideType? = null,
    ): NewDependencyWithChildren {
        val parameter = type.declaration

        val cannotBeDependency = cannotBeDependency(parameter, commandDependenciesProps)

        val children =
            if (shouldFindChildren(!cannotBeDependency, parameter))
                getNewChildren(type, parameter, commandDependenciesProps)
            else null

        val isCommandDependency = commandDependenciesProps.contains(type.declaration)
        val requiresCommandDependencies =
            isCommandDependency || children?.requireCommandDependencies == true

        val metadata =
            createDependencyMetadata(
                type,
                isCommandDependency,
                cannotBeDependency,
                requiresCommandDependencies,
                dependencyTypeOverride,
            )

        // FIXME need to know if override dependencies were external and can be autoloaded
        // Need multiple modules to test
        // May need to check all versions of dependency and allow autoloading if any are
        // autoloadable
        return NewDependencyWithChildren(
            DependencyWithChildren(
                metadata,
                children?.topLevelChildren ?: emptyList(),
                cannotBeAutoloaded(parameter, children),
            ),
            children?.allDependencies ?: emptySet(),
            parentCannotBeAutoloaded(parameter, isCommandDependency, cannotBeDependency),
            requiresCommandDependencies,
        )
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
            val childType = childParameter.resolveTypeUsingParent(parentType)
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

    @OptIn(ExperimentalContracts::class)
    private fun shouldFindChildren(
        isPossibleDependency: Boolean,
        parameter: KSDeclaration,
    ): Boolean {
        contract { returns(true) implies (parameter is KSClassDeclaration) }

        return parameter is KSClassDeclaration && isPossibleDependency && !mustBeRoot(parameter)
    }

    private fun mustBeRoot(parameter: KSDeclaration): Boolean {
        return parameter.packageName.asString() == kbusBusPackageName
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
    isCommandDependency: Boolean,
    cannotBeDependency: Boolean,
    requiresCommandDependencies: Boolean,
    dependencyTypeOverride: DependencyOverrideType?,
): Dependency {
    return if (isCommandDependency) {
        CommandDependency(type.toTypeName())
    } else if (cannotBeDependency) {
        NonDependency(type.toTypeName())
    } else if (dependencyTypeOverride != null) {
        Dependency.fromDependencyOverrideType(
            dependencyTypeOverride,
            type.toTypeName(),
            requiresCommandDependencies,
        )
    } else if (requiresCommandDependencies) {
        FunctionalDependency(type.toTypeName(), true)
    } else {
        PropertyDependency(type.toTypeName())
    }
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

private fun cannotBeAutoloaded(parameter: KSDeclaration, children: ChildrenDependencies?): Boolean {
    return children == null ||
        children.topLevelChildren.isEmpty() ||
        children.parentCannotBeAutoloaded ||
        isExternalDependency(parameter)
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
