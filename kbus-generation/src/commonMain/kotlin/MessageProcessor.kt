package com.jimbroze.kbus.generation

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.validate
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.jimbroze.kbus.annotations.Load
import com.jimbroze.kbus.core.Command
import com.jimbroze.kbus.core.Query
import com.jimbroze.kbus.generation.DependencyLoaderGenerator.generateDependencyLoader

private val loadableMessages = listOf(Command::class, Query::class)

class MessageProcessor(private val codeGenerator: CodeGenerator, private val logger: KSPLogger) :
    SymbolProcessor {
    private val loadedMessageGenerator =
        LoadedMessageGenerator(codeGenerator, logger, loadableMessages)

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols =
            resolver
                .getSymbolsWithAnnotation(Load::class.qualifiedName.toString())
                .filterIsInstance<KSClassDeclaration>()

        if (!symbols.iterator().hasNext()) return emptyList()

        val loadedMessages = mutableSetOf<LoadedHandlerDefinition>()

        for (symbol in symbols) {
            symbol.accept(MessageClassVisitor(), Unit)?.let { loadedMessages.add(it) }
        }

        generateDependencyLoader(codeGenerator, loadedMessages)

        val messagesThatCouldNotBeProcessed = symbols.filterNot { it.validate() }.toList()
        return messagesThatCouldNotBeProcessed
    }

    inner class MessageClassVisitor : KSDefaultVisitor<Unit, LoadedHandlerDefinition?>() {
        override fun defaultHandler(node: KSNode, data: Unit): LoadedHandlerDefinition? {
            return null
        }

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration,
            data: Unit,
        ): LoadedHandlerDefinition? {
            if (classDeclaration.classKind != ClassKind.CLASS) {
                logger.error(
                    "Only classes can be annotated with @${Load::class.simpleName}",
                    classDeclaration,
                )
                return null
            }

            return visitMessageHandler(classDeclaration)
        }

        private fun visitMessageHandler(
            classDeclaration: KSClassDeclaration
        ): LoadedHandlerDefinition? {
            return loadedMessageGenerator.generateLoadedMessage(classDeclaration)
        }
    }
}
