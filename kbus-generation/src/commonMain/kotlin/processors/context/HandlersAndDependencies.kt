package com.jimbroze.kbus.generation.processors.visitors

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependencyProperties
import com.jimbroze.kbus.generation.processing.dependencies.Dependencies
import com.jimbroze.kbus.generation.processing.dependencies.DependencyIndexFactory
import com.jimbroze.kbus.generation.processing.dependencies.DependencyWithChildren
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.HandlerFactory

class HandlersAndDependencies {
    private val _allDependencies = mutableSetOf<DependencyWithChildren>()
    private val _handlers = mutableMapOf<String, HandlerDefinition>()

    val allDependencies: Set<DependencyWithChildren>
        get() = _allDependencies

    val handlers: Set<HandlerDefinition>
        get() = _handlers.values.toSet()

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
        _allDependencies.addAll(dependencies.allDependencies)

        // TODO do this before any handler generation?
        if (_handlers.containsKey(classDeclaration.qualifiedName!!.asString())) return
        val handler =
            handlerFactory.createHandler(classDeclaration, dependencies.topLevelDependencies)
                ?: return
        validateCanAddHandler(handler, logger)

        _handlers[handler.handlerData.handlerClass.canonicalName] = handler
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

    fun addIndexedHandlers(
        handlerInfoAnnotations: List<KSAnnotation>,
        dependencyIndexFactory: DependencyIndexFactory,
        logger: KSPLogger,
    ) {
        val handlers =
            dependencyIndexFactory.createHandlers(
                handlerInfoAnnotations,
                allDependencies.map { it.metadata }.toSet(),
            )

        handlers.associateByTo(_handlers) { handler ->
            handler.handlerData.handlerClass.canonicalName
        }
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
                newHandler.handlerData.messageClass == other.handlerData.messageClass
            }

        handlerUsingSameMessageOrNull?.let {
            val messageClassName = newHandler.handlerData.messageClass.simpleName
            val oldHandlerName = handlerUsingSameMessageOrNull.handlerData.handlerClass.simpleName
            val newHandlerName = newHandler.handlerData.handlerClass.simpleName
            logger.error(
                "Message class $messageClassName is used by multiple handlers: '$oldHandlerName' & '$newHandlerName'"
            )
        }
    }
}
