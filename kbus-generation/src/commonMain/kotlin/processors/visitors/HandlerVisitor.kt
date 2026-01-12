package com.jimbroze.kbus.generation.processors.visitors

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.jimbroze.kbus.generation.CommandDependencyProperties
import com.jimbroze.kbus.generation.DependencyNested
import com.jimbroze.kbus.generation.HandlerDefinition
import com.jimbroze.kbus.generation.HandlerFactory

// class HandlerVisitor(
//    private val logger: KSPLogger,
//    private val dependencyFactory: DependencyFactory,
//    private val handlerFactory: HandlerFactory,
// ) {
//    fun addHandler(
//        data: HandlersContext,
//        classDeclaration: KSClassDeclaration,
//        commandDependenciesProps: CommandDependencyProperties,
//    ) {
//        val dependencies =
//            dependencyFactory.generateChildDependencies(
//                classDeclaration.asStarProjectedType(),
//                commandDependenciesProps,
//            )
//
//        val handler =
//            handlerFactory.createHandler(classDeclaration, dependencies.topLevelDependencies)
//                ?: return
//
//        data.addHandler(handler, classDeclaration.qualifiedName!!, dependencies.allDependencies)
//    }
// }

class HandlersContext {
    private val _allDependencies = mutableSetOf<DependencyNested>()
    private val _handlers = mutableSetOf<HandlerDefinition>()

    val allDependencies: Set<DependencyNested>
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

    //    fun addHandler(
    //        definition: HandlerDefinition,
    //        classQualifiedName: KSName,
    //        dependencies: Set<DependencyNested>,
    //    ) {
    //
    //        _handlers.add(definition)
    //        _allDependencies.addAll(dependencies)
    //        rootPackageName.addName(classQualifiedName)
    //    }
}
