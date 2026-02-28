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
import com.jimbroze.kbus.generation.processors.context.HandlersAndDependencies

class DependencyIndexVisitor(private val indexParser: IndexParser, private val logger: KSPLogger) :
    KSDefaultVisitor<HandlersAndDependencies, Unit>() {
    override fun defaultHandler(node: KSNode, data: HandlersAndDependencies) {
        logger.error("Only classes can be annotated with @${KbusIndex::class.simpleName}", node)
    }

    override fun visitClassDeclaration(
        classDeclaration: KSClassDeclaration,
        data: HandlersAndDependencies,
    ) {
        if (classDeclaration.classKind != ClassKind.CLASS) {
            logger.error(
                "Only classes can be annotated with @${KbusIndex::class.simpleName}. " +
                    "$classDeclaration is a ${classDeclaration.classKind}",
                classDeclaration,
            )
        }

        val kbusIndexAnnotation =
            classDeclaration.annotations.find {
                it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                    KbusIndex::class.qualifiedName
            } ?: error("Missing @${KbusIndex::class.simpleName} annotation")

        addDependencies(kbusIndexAnnotation, data)
        addHandlers(kbusIndexAnnotation, data)
    }

    private fun addDependencies(kbusIndexAnnotation: KSAnnotation, data: HandlersAndDependencies) {
        val dependenciesArg =
            kbusIndexAnnotation.arguments.find {
                it.name?.asString() == KbusIndex::dependencies.name
            }

        @Suppress("UNCHECKED_CAST")
        val dependencyInfoAnnotations = dependenciesArg?.value as? List<KSAnnotation> ?: emptyList()

        val dependencies = indexParser.createDependencies(dependencyInfoAnnotations)
        for (dependency in dependencies.allDependencies) {
            when (val result = data.tryAddDependency(dependency)) {
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

    private fun addHandlers(kbusIndexAnnotation: KSAnnotation, data: HandlersAndDependencies) {
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
                    )
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
}
