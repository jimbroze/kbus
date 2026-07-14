package com.jimbroze.kbus.generation.processors.visitors

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.jimbroze.kbus.contracts.annotations.LoadEvent
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.generation.processing.ConflictPolicy
import com.jimbroze.kbus.generation.processing.autopublish.AutoPublishDefinition
import com.jimbroze.kbus.generation.processing.autopublish.AutoPublishFactory
import com.jimbroze.kbus.generation.processors.context.ProcessingContext
import com.jimbroze.kbus.generation.utility.extendsType
import com.squareup.kotlinpoet.ksp.toClassName

class LoadEventVisitor(
    private val autoPublishFactory: AutoPublishFactory,
    private val logger: KSPLogger,
) : KSDefaultVisitor<ProcessingContext, Unit>() {

    override fun defaultHandler(node: KSNode, data: ProcessingContext) {
        logger.error(
            "Only classes can be annotated with @${LoadEvent::class.simpleName}. $node is not a class",
            node,
        )
    }

    override fun visitClassDeclaration(
        classDeclaration: KSClassDeclaration,
        data: ProcessingContext,
    ) {
        val alreadyKnown = data.hasAutoPublish(classDeclaration.toClassName())
        if (!isValidLoadEventTarget(classDeclaration) || alreadyKnown) return

        val definition = autoPublishFactory.create(classDeclaration) ?: return

        registerAutoPublish(definition, classDeclaration, data)
    }

    private fun isValidLoadEventTarget(classDeclaration: KSClassDeclaration): Boolean =
        when {
            classDeclaration.classKind != ClassKind.CLASS -> {
                logger.error(
                    "Only classes can be annotated with @${LoadEvent::class.simpleName}. " +
                        "$classDeclaration is a ${classDeclaration.classKind}",
                    classDeclaration,
                )
                false
            }

            !classDeclaration.extendsType(Event::class.qualifiedName!!) -> {
                logger.error(
                    "Only ${Event::class.simpleName} classes can be annotated with " +
                        "@${LoadEvent::class.simpleName}",
                    classDeclaration,
                )
                false
            }

            else -> true
        }

    private fun registerAutoPublish(
        definition: AutoPublishDefinition,
        classDeclaration: KSClassDeclaration,
        data: ProcessingContext,
    ) {
        classDeclaration.containingFile?.let { data.addSourceFile(it) }

        when (val result = data.tryAddAutoPublish(definition)) {
            is ConflictPolicy.Result.Accept -> {
                // Successfully added
            }

            is ConflictPolicy.Result.ExactDuplicate -> {
                // Duplicate definition is fine
            }

            is ConflictPolicy.Result.InvalidConflict -> {
                logger.error(result.reason, classDeclaration)
            }
        }
    }
}
