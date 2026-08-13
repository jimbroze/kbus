package com.jimbroze.kbus.generation.processors.visitors

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.jimbroze.kbus.api.annotations.LoadEventMapper
import com.jimbroze.kbus.generation.processing.ConflictPolicy
import com.jimbroze.kbus.generation.processing.autopublish.AutoPublishDefinition
import com.jimbroze.kbus.generation.processing.autopublish.AutoPublishFactory
import com.jimbroze.kbus.generation.processors.context.ProcessingContext
import com.squareup.kotlinpoet.ksp.toClassName

class LoadEventMapperVisitor(
    private val autoPublishFactory: AutoPublishFactory,
    private val logger: KSPLogger,
) : KSDefaultVisitor<ProcessingContext, Unit>() {

    override fun defaultHandler(node: KSNode, data: ProcessingContext) {
        logger.error(
            "Only objects can be annotated with @${LoadEventMapper::class.simpleName}. " +
                "$node is not an object",
            node,
        )
    }

    override fun visitClassDeclaration(
        classDeclaration: KSClassDeclaration,
        data: ProcessingContext,
    ) {
        val alreadyKnown = data.hasAutoPublish(classDeclaration.toClassName())
        if (!isValidMapperTarget(classDeclaration) || alreadyKnown) return

        val definition = autoPublishFactory.create(classDeclaration) ?: return

        registerAutoPublish(definition, classDeclaration, data)
    }

    /**
     * The registration list is a top-level value with nothing to resolve a mapper from, so a mapper
     * has to be referenceable by name alone.
     */
    private fun isValidMapperTarget(classDeclaration: KSClassDeclaration): Boolean =
        when (classDeclaration.classKind) {
            ClassKind.OBJECT -> true

            else -> {
                logger.error(
                    "Only objects can be annotated with @${LoadEventMapper::class.simpleName}. " +
                        "$classDeclaration is a ${classDeclaration.classKind}",
                    classDeclaration,
                )
                false
            }
        }

    private fun registerAutoPublish(
        definition: AutoPublishDefinition,
        classDeclaration: KSClassDeclaration,
        data: ProcessingContext,
    ) {
        classDeclaration.containingFile?.let { data.addSourceFile(it) }

        when (val result = data.tryAddAutoPublish(definition)) {
            is ConflictPolicy.Result.Accept,
            is ConflictPolicy.Result.ExactDuplicate -> Unit

            is ConflictPolicy.Result.InvalidConflict -> {
                logger.error(result.reason, classDeclaration)
            }
        }
    }
}
