package com.jimbroze.kbus.generation.processors.visitors

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependencyProperties
import com.jimbroze.kbus.generation.processing.dependencies.Dependencies
import com.jimbroze.kbus.generation.processing.dependencies.DependencyIndexFactory
import com.jimbroze.kbus.generation.processing.dependencies.DependencyOverrideType
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
        logger: KSPLogger,
    ) {
        val dependencies =
            handlerFactory.dependencyFactory.generateChildDependencies(
                classDeclaration.asStarProjectedType(),
                classDeclaration,
                commandDependenciesProps,
            )
        validateNoDuplicateDependencies(dependencies, logger)

        val handler =
            handlerFactory.createHandler(classDeclaration, dependencies.topLevelDependencies)
                ?: return
        validateCanAddHandler(handler, logger)

        _handlers.add(handler)
        _allDependencies.addAll(dependencies.allDependencies)
    }

    fun addDependency(
        dependencyTypeRef: KSTypeReference,
        commandDependenciesProps: CommandDependencyProperties,
        handlerFactory: HandlerFactory,
        logger: KSPLogger,
        dependencyTypeOverride: DependencyOverrideType? = null,
    ) {
        val dependencies =
            handlerFactory.dependencyFactory.generateDependencyWithChildren(
                dependencyTypeRef.resolve(),
                commandDependenciesProps,
                dependencyTypeOverride,
            )
        validateNoDuplicateDependencies(dependencies, logger)

        _allDependencies.addAll(dependencies.allDependencies)
    }

    fun addIndexedDependencies(
        dependencyInfoAnnotations: List<KSAnnotation>,
        dependencyIndexFactory: DependencyIndexFactory,
        logger: KSPLogger,
    ) {
        val dependencies = dependencyIndexFactory.createDependencies(dependencyInfoAnnotations)
        validateNoDuplicateDependencies(dependencies, logger)

        _allDependencies.addAll(dependencies.allDependencies)
    }

    fun isEmpty() = handlers.isEmpty() && allDependencies.isEmpty()

    private fun validateNoDuplicateDependencies(dependencies: Dependencies, logger: KSPLogger) {
        for (dependency in dependencies.allDependencies) {
            val isDuplicate =
                allDependencies.any { other ->
                    dependency.metadata.hasConflictingNameWith(other.metadata)
                }

            if (isDuplicate) {
                val dependencyName = dependency.metadata.name
                logger.error("Tried to generate multiple dependencies for $dependencyName")
            }
        }
    }

    private fun validateCanAddHandler(newHandler: HandlerDefinition, logger: KSPLogger) {
        val handlerUsingSameMessageOrNull =
            handlers.firstOrNull { other ->
                newHandler.handlerData.messageClass.simpleName == other.handlerData.messageClass
            }

        handlerUsingSameMessageOrNull?.let {
            val messageClassName = newHandler.handlerData.messageClass.simpleName.asString()
            val oldHandlerName =
                handlerUsingSameMessageOrNull.handlerData.handlerClass.simpleName.asString()
            val newHandlerName = newHandler.handlerData.handlerClass.simpleName.asString()
            logger.error(
                "Message class $messageClassName is used by multiple handlers: '$oldHandlerName' & '$newHandlerName'",
                newHandler.handlerData.messageClass,
            )
        }
    }
}
