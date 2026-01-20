package com.jimbroze.kbus.generation.processors.visitors

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependencyProperties
import com.jimbroze.kbus.generation.processing.dependencies.DependencyType
import com.jimbroze.kbus.generation.processing.dependencies.DependencyWithChildren
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.HandlerFactory

class HandlersAndDependencies {
    private val _allDependencies = mutableSetOf<DependencyWithChildren>()
    private val _handlers = mutableSetOf<HandlerDefinition>()

    val allDependencies: Set<DependencyWithChildren>
        get() = _allDependencies

    val handlers: Set<HandlerDefinition>
        get() = _handlers

    fun addHandler(
        classDeclaration: KSClassDeclaration,
        commandDependenciesProps: CommandDependencyProperties,
        handlerFactory: HandlerFactory,
    ) {
        val dependencies =
            handlerFactory.dependencyFactory.generateChildDependencies(
                classDeclaration.asStarProjectedType(),
                commandDependenciesProps,
            )

        val handler =
            handlerFactory.createHandler(classDeclaration, dependencies.topLevelDependencies)
                ?: return

        _handlers.add(handler)
        _allDependencies.addAll(dependencies.allDependencies)
    }

    fun addDependency(
        dependencyTypeRef: KSTypeReference,
        commandDependenciesProps: CommandDependencyProperties,
        handlerFactory: HandlerFactory,
        dependencyTypeOverride: DependencyType? = null,
    ) {
        val dependencies =
            handlerFactory.dependencyFactory.generateDependencyWithChildren(
                dependencyTypeRef.resolve(),
                commandDependenciesProps,
                dependencyTypeOverride,
            )

        _allDependencies.addAll(dependencies.allDependencies)
    }

    fun isEmpty() = handlers.isEmpty()
}
