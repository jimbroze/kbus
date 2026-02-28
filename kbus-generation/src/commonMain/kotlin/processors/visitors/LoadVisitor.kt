package com.jimbroze.kbus.generation.processors.visitors

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.generation.processing.ConflictPolicy
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependencyProperties
import com.jimbroze.kbus.generation.processing.dependencies.Dependencies
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.HandlerFactory
import com.jimbroze.kbus.generation.processors.context.HandlersAndDependencies
import com.squareup.kotlinpoet.ksp.toClassName

class LoadVisitor(
    private val commandDependenciesProps: CommandDependencyProperties,
    private val handlerFactory: HandlerFactory,
    private val logger: KSPLogger,
) : KSDefaultVisitor<HandlersAndDependencies, Unit>() {

    override fun defaultHandler(node: KSNode, data: HandlersAndDependencies) {
        logger.error(
            "Only classes can be annotated with @${LoadMessageHandler::class.simpleName}. " +
                "$node is not a class",
            node,
        )
    }

    override fun visitClassDeclaration(
        classDeclaration: KSClassDeclaration,
        data: HandlersAndDependencies,
    ) {
        if (classDeclaration.classKind != ClassKind.CLASS) {
            logger.error(
                "Only classes can be annotated with @${LoadMessageHandler::class.simpleName}. " +
                    "$classDeclaration is a ${classDeclaration.classKind}",
                classDeclaration,
            )
        }

        if (data.hasHandler(classDeclaration.toClassName())) return

        // TODO - perf - generate just top-levels to check if they already exist?
        val dependencies =
            handlerFactory.dependencyFactory.generateChildDependencies(
                classDeclaration.asStarProjectedType(),
                classDeclaration,
                commandDependenciesProps,
            )

        val handler =
            handlerFactory.createHandler(classDeclaration, dependencies.topLevelDependencies)
        if (handler === null) return

        addDependencies(dependencies, data, classDeclaration)

        addHandler(data, handler, classDeclaration)
    }

    private fun addDependencies(
        dependencies: Dependencies,
        data: HandlersAndDependencies,
        handlerClass: KSClassDeclaration,
    ) {
        for (dependency in dependencies.allDependencies) {
            when (val result = data.tryAddDependency(dependency)) {
                is ConflictPolicy.Result.Accept -> {
                    // Successfully added
                }

                is ConflictPolicy.Result.ExactDuplicate -> {
                    // Duplicate dependency is fine
                }

                is ConflictPolicy.Result.InvalidConflict -> {
                    logger.error(result.reason, handlerClass)
                }
            }
        }
    }

    private fun addHandler(
        data: HandlersAndDependencies,
        handler: HandlerDefinition,
        classDeclaration: KSClassDeclaration,
    ) {
        when (val result = data.tryAddHandler(handler)) {
            is ConflictPolicy.Result.Accept -> {
                // Successfully added
            }

            is ConflictPolicy.Result.ExactDuplicate -> {
                // Duplicate handler is fine
            }

            is ConflictPolicy.Result.InvalidConflict -> {
                logger.error(result.reason, classDeclaration)
            }
        }
    }
}
