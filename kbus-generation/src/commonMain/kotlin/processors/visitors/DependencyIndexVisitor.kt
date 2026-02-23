package com.jimbroze.kbus.generation.processors.visitors

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.jimbroze.kbus.annotations.KbusIndex
import com.jimbroze.kbus.generation.processing.dependencies.DependencyIndexFactory

class DependencyIndexVisitor(
    private val dependencyIndexFactory: DependencyIndexFactory,
    private val logger: KSPLogger,
) : KSDefaultVisitor<HandlersAndDependencies, Unit>() {
    override fun defaultHandler(node: KSNode, data: HandlersAndDependencies) {
        error("Only classes can be annotated with @${KbusIndex::class.simpleName}")
    }

    override fun visitClassDeclaration(
        classDeclaration: KSClassDeclaration,
        data: HandlersAndDependencies,
    ) {
        if (classDeclaration.classKind != ClassKind.CLASS) {
            error(
                "Only classes can be annotated with @${KbusIndex::class.simpleName}. " +
                    "$classDeclaration is a ${classDeclaration.classKind}"
            )
        }

        val kbusIndexAnnotation =
            classDeclaration.annotations.find {
                it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                    KbusIndex::class.qualifiedName
            } ?: error("Missing @${KbusIndex::class.simpleName} annotation")

        // TODO don't pass factory to data
        addDependencies(kbusIndexAnnotation, data)
        addHandlers(kbusIndexAnnotation, data)
    }

    private fun addDependencies(kbusIndexAnnotation: KSAnnotation, data: HandlersAndDependencies) {
        val dependenciesArg =
            kbusIndexAnnotation.arguments.find {
                it.name?.asString() == KbusIndex::dependencies.name
            }

        @Suppress("UNCHECKED_CAST")
        val dependencyInfos = dependenciesArg?.value as? List<KSAnnotation> ?: emptyList()

        data.addIndexedDependencies(dependencyInfos, dependencyIndexFactory, logger)
    }

    private fun addHandlers(kbusIndexAnnotation: KSAnnotation, data: HandlersAndDependencies) {
        val handlersArg =
            kbusIndexAnnotation.arguments.find { it.name?.asString() == KbusIndex::handlers.name }

        @Suppress("UNCHECKED_CAST")
        val handlerInfos = handlersArg?.value as? List<KSAnnotation> ?: emptyList()

        data.addIndexedHandlers(handlerInfos, dependencyIndexFactory, logger)
    }
}
