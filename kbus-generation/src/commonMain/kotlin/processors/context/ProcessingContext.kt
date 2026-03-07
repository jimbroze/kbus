package com.jimbroze.kbus.generation.processors.context

import com.google.devtools.ksp.symbol.KSFile
import com.jimbroze.kbus.generation.processing.ConflictPolicy
import com.jimbroze.kbus.generation.processing.dependencies.DependencyConflictPolicy
import com.jimbroze.kbus.generation.processing.dependencies.DependencyWithChildren
import com.jimbroze.kbus.generation.processing.handlers.HandlerConflictPolicy
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.squareup.kotlinpoet.ClassName

class ProcessingContext {
    private val _allDependencies = mutableSetOf<DependencyWithChildren>()
    private val _handlers = mutableMapOf<String, HandlerDefinition>()
    private val _sourceFiles = mutableSetOf<KSFile>()

    val allDependencies: Set<DependencyWithChildren>
        get() = _allDependencies

    val handlers: Set<HandlerDefinition>
        get() = _handlers.values.toSet()

    val sourceFiles: Set<KSFile>
        get() = _sourceFiles

    fun addSourceFile(file: KSFile) {
        _sourceFiles.add(file)
    }

    fun hasHandler(handlerClass: ClassName): Boolean =
        _handlers.containsKey(handlerClass.canonicalName)

    fun tryAddHandler(handler: HandlerDefinition): ConflictPolicy.Result {
        if (hasHandler(handler.handlerData.handlerClass))
            return ConflictPolicy.Result.ExactDuplicate

        val result = HandlerConflictPolicy.evaluate(handler, handlers)
        if (result is ConflictPolicy.Result.Accept) {
            _handlers[handler.handlerData.handlerClass.canonicalName] = handler
        }
        return result
    }

    fun tryAddDependency(dependency: DependencyWithChildren): ConflictPolicy.Result {
        val result = DependencyConflictPolicy.evaluate(dependency, allDependencies)
        if (result is ConflictPolicy.Result.Accept) {
            _allDependencies.add(dependency)
        }
        return result
    }

    fun isEmpty() = handlers.isEmpty() && allDependencies.isEmpty()
}
