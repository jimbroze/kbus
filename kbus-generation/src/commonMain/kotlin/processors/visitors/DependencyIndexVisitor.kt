package com.jimbroze.kbus.generation.processors.visitors

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.jimbroze.kbus.contracts.annotations.index.KbusIndex
import com.jimbroze.kbus.generation.processing.ConflictPolicy
import com.jimbroze.kbus.generation.processing.IndexParser
import com.jimbroze.kbus.generation.processors.context.ProcessingContext

class DependencyIndexVisitor(private val indexParser: IndexParser, private val logger: KSPLogger) :
    KSDefaultVisitor<ProcessingContext, Unit>() {
    override fun defaultHandler(node: KSNode, data: ProcessingContext) {
        logger.error("Only classes can be annotated with @${KbusIndex::class.simpleName}", node)
    }

    override fun visitClassDeclaration(
        classDeclaration: KSClassDeclaration,
        data: ProcessingContext,
    ) {
        if (classDeclaration.classKind != ClassKind.CLASS) {
            logger.error(
                "Only classes can be annotated with @${KbusIndex::class.simpleName}. " +
                    "$classDeclaration is a ${classDeclaration.classKind}",
                classDeclaration,
            )
        }

        classDeclaration.containingFile?.let { data.addSourceFile(it) }

        val kbusIndexAnnotation =
            classDeclaration.annotations.find {
                it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                    KbusIndex::class.qualifiedName
            } ?: error("Missing @${KbusIndex::class.simpleName} annotation")

        addDependencies(kbusIndexAnnotation, data)
        addHandlers(kbusIndexAnnotation, data)
        addAutoPublishEvents(kbusIndexAnnotation, data)
        addContextCommands(kbusIndexAnnotation, data)
    }

    private fun addContextCommands(kbusIndexAnnotation: KSAnnotation, data: ProcessingContext) {
        val contextCommandsArg =
            kbusIndexAnnotation.arguments.find {
                it.name?.asString() == KbusIndex::contextCommands.name
            }

        @Suppress("UNCHECKED_CAST")
        val contextCommandsInfoAnnotations =
            contextCommandsArg?.value as? List<KSAnnotation> ?: emptyList()

        indexParser.createContextCommandsInterfaces(contextCommandsInfoAnnotations).forEach {
            (contextIdentity, interfaceClass) ->
            data.addContextCommandsInterface(contextIdentity, interfaceClass)
        }
    }

    private fun addDependencies(kbusIndexAnnotation: KSAnnotation, data: ProcessingContext) {
        val dependenciesArg =
            kbusIndexAnnotation.arguments.find {
                it.name?.asString() == KbusIndex::dependencies.name
            }

        @Suppress("UNCHECKED_CAST")
        val dependencyInfoAnnotations = dependenciesArg?.value as? List<KSAnnotation> ?: emptyList()

        val dependencies = indexParser.createDependencies(dependencyInfoAnnotations)
        for (dependency in dependencies.allDependencies) {
            when (val result = data.tryAddDependency(dependency, learnedFromIndex = true)) {
                is ConflictPolicy.Result.Accept -> {
                    // Successfully added
                }
                is ConflictPolicy.Result.ExactDuplicate -> {
                    // Duplicate dependency is fine
                }
                is ConflictPolicy.Result.InvalidConflict -> {
                    logger.error(result.reason, dependenciesArg)
                }
            }
        }
    }

    private fun addHandlers(kbusIndexAnnotation: KSAnnotation, data: ProcessingContext) {
        val handlersArg =
            kbusIndexAnnotation.arguments.find { it.name?.asString() == KbusIndex::handlers.name }

        @Suppress("UNCHECKED_CAST")
        val handlerInfoAnnotations = handlersArg?.value as? List<KSAnnotation> ?: emptyList()

        for (handlerInfoAnnotation in handlerInfoAnnotations) {
            val result =
                data.tryAddHandler(
                    indexParser.createHandlerFromAnnotation(
                        handlerInfoAnnotation,
                        data.allDependencies.map { it.metadata }.toSet(),
                    ),
                    learnedFromIndex = true,
                )
            when (result) {
                is ConflictPolicy.Result.Accept -> {
                    // Successfully added
                }
                is ConflictPolicy.Result.ExactDuplicate -> {
                    // Duplicate handler is fine
                }
                is ConflictPolicy.Result.InvalidConflict -> {
                    logger.error(result.reason, handlerInfoAnnotation)
                }
            }
        }
    }

    private fun addAutoPublishEvents(kbusIndexAnnotation: KSAnnotation, data: ProcessingContext) {
        val autoPublishEventsArg =
            kbusIndexAnnotation.arguments.find {
                it.name?.asString() == KbusIndex::autoPublishEvents.name
            }

        @Suppress("UNCHECKED_CAST")
        val autoPublishInfoAnnotations =
            autoPublishEventsArg?.value as? List<KSAnnotation> ?: emptyList()

        val definitions = indexParser.createAutoPublishDefinitions(autoPublishInfoAnnotations)
        for (definition in definitions) {
            when (val result = data.tryAddAutoPublish(definition, learnedFromIndex = true)) {
                is ConflictPolicy.Result.Accept -> {
                    // Successfully added
                }
                is ConflictPolicy.Result.ExactDuplicate -> {
                    // Duplicate definition is fine
                }
                is ConflictPolicy.Result.InvalidConflict -> {
                    logger.error(result.reason, autoPublishEventsArg)
                }
            }
        }
    }
}
