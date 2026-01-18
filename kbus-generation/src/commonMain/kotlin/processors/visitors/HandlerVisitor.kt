package com.jimbroze.kbus.generation.processors.visitors

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependencyProperties
import com.jimbroze.kbus.generation.processing.dependencies.DependencyWithChildren
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.HandlerFactory

class HandlersContext {
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
    ) {
        val dependencyType = dependencyTypeRef.resolve()
        val dependencies =
            handlerFactory.dependencyFactory.generateDependencyWithChildren(
                dependencyType,
                commandDependenciesProps,
            )

        _allDependencies.addAll(dependencies.allDependencies)
    }

    fun isEmpty() = handlers.isEmpty()
}
