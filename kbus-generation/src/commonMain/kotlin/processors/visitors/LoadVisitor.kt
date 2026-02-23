package com.jimbroze.kbus.generation.processors.visitors

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.jimbroze.kbus.annotations.LoadMessageHandler
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependencyProperties
import com.jimbroze.kbus.generation.processing.handlers.HandlerFactory

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

        data.addHandler(classDeclaration, commandDependenciesProps, handlerFactory, logger)
    }
}
