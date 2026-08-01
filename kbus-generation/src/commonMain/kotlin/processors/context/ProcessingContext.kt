package com.jimbroze.kbus.generation.processors.context

import com.google.devtools.ksp.symbol.KSFile
import com.jimbroze.kbus.generation.processing.ConflictPolicy
import com.jimbroze.kbus.generation.processing.autopublish.AutoPublishConflictPolicy
import com.jimbroze.kbus.generation.processing.autopublish.AutoPublishDefinition
import com.jimbroze.kbus.generation.processing.dependencies.DependencyConflictPolicy
import com.jimbroze.kbus.generation.processing.dependencies.DependencyWithChildren
import com.jimbroze.kbus.generation.processing.handlers.HandlerConflictPolicy
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.squareup.kotlinpoet.ClassName

/**
 * What a module can see, and — separately — what it declares itself. A module learns its
 * dependencies' handlers and dependencies from their `@KbusIndex` metadata so it can generate
 * against them, but its own index must carry only what it declares, or every module downstream
 * would re-export the whole graph.
 */
class ProcessingContext {
    private val _allDependencies = mutableSetOf<DependencyWithChildren>()
    private val _handlers = mutableMapOf<String, HandlerDefinition>()
    private val _autoPublishDefinitions = mutableMapOf<String, AutoPublishDefinition>()
    private val _sourceFiles = mutableSetOf<KSFile>()
    private val locallyDeclaredHandlerKeys = mutableSetOf<String>()
    private val locallyDeclaredDependencySignatures = mutableSetOf<String>()
    private val locallyDeclaredAutoPublishKeys = mutableSetOf<String>()

    val allDependencies: Set<DependencyWithChildren>
        get() = _allDependencies

    val handlers: Set<HandlerDefinition>
        get() = _handlers.values.toSet()

    val autoPublishDefinitions: Set<AutoPublishDefinition>
        get() = _autoPublishDefinitions.values.toSet()

    val sourceFiles: Set<KSFile>
        get() = _sourceFiles

    val locallyDeclaredHandlers: Set<HandlerDefinition>
        get() = _handlers.filterKeys { it in locallyDeclaredHandlerKeys }.values.toSet()

    val locallyDeclaredDependencies: Set<DependencyWithChildren>
        get() =
            _allDependencies.filterTo(mutableSetOf()) {
                it.metadata.signature in locallyDeclaredDependencySignatures
            }

    val locallyDeclaredAutoPublishDefinitions: Set<AutoPublishDefinition>
        get() =
            _autoPublishDefinitions
                .filterKeys { it in locallyDeclaredAutoPublishKeys }
                .values
                .toSet()

    fun addSourceFile(file: KSFile) {
        _sourceFiles.add(file)
    }

    fun hasHandler(handlerClass: ClassName): Boolean =
        _handlers.containsKey(handlerClass.canonicalName)

    fun hasAutoPublish(integrationEventClass: ClassName): Boolean =
        _autoPublishDefinitions.containsKey(integrationEventClass.canonicalName)

    fun tryAddHandler(
        handler: HandlerDefinition,
        learnedFromIndex: Boolean = false,
    ): ConflictPolicy.Result {
        val key = handler.handlerData.handlerClass.canonicalName
        if (!learnedFromIndex) locallyDeclaredHandlerKeys.add(key)
        if (hasHandler(handler.handlerData.handlerClass))
            return ConflictPolicy.Result.ExactDuplicate

        val result = HandlerConflictPolicy.evaluate(handler, handlers)
        if (result is ConflictPolicy.Result.Accept) {
            _handlers[key] = handler
        }
        return result
    }

    fun tryAddDependency(
        dependency: DependencyWithChildren,
        learnedFromIndex: Boolean = false,
    ): ConflictPolicy.Result {
        if (!learnedFromIndex)
            locallyDeclaredDependencySignatures.add(dependency.metadata.signature)
        val result = DependencyConflictPolicy.evaluate(dependency, allDependencies)
        if (result is ConflictPolicy.Result.Accept) {
            _allDependencies.add(dependency)
        }
        return result
    }

    fun tryAddAutoPublish(
        definition: AutoPublishDefinition,
        learnedFromIndex: Boolean = false,
    ): ConflictPolicy.Result {
        if (!learnedFromIndex)
            locallyDeclaredAutoPublishKeys.add(definition.integrationEventClass.canonicalName)
        val result = AutoPublishConflictPolicy.evaluate(definition, autoPublishDefinitions)
        if (result is ConflictPolicy.Result.Accept) {
            _autoPublishDefinitions[definition.integrationEventClass.canonicalName] = definition
        }
        return result
    }

    fun isEmpty() =
        handlers.isEmpty() && allDependencies.isEmpty() && autoPublishDefinitions.isEmpty()
}
