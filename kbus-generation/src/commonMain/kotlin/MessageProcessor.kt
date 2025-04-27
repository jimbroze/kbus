package com.jimbroze.kbus.generation

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

class MessageProcessor(
    private val logger: KSPLogger,
    private val dependencyProcessor: DependencyProcessor,
    private val dependencyLoaderGenerator: ContainerGenerator,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val messagesToLoad = resolver.getSymbolsWithAnnotation(Load::class.qualifiedName.toString())

        processMessagesToLoad(messagesToLoad)

        return messagesToLoad.filterNot { it.validate() }.toList()
    }

    private fun processMessagesToLoad(symbols: Sequence<KSAnnotated>) {
        val dependencies = mutableSetOf<NestedDependency>()

        for (symbol in symbols) {
            symbol.accept(LoadVisitor(), Unit).let { dependencies.addAll(it) }
        }

        if (dependencies.isEmpty()) return

        dependencyLoaderGenerator.generateLoaderInterface(dependencies)
    }

    inner class LoadVisitor : KSDefaultVisitor<Unit, Set<NestedDependency>>() {
        override fun defaultHandler(node: KSNode, data: Unit): Set<NestedDependency> {
            return emptySet()
        }

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration,
            data: Unit,
        ): Set<NestedDependency> {
            if (classDeclaration.classKind != ClassKind.CLASS) {
                logger.error(
                    "Only classes can be annotated with @${Load::class.simpleName}",
                    classDeclaration,
                )
                return emptySet()
            }

            return dependencyProcessor.generateFrom(
                classDeclaration.asStarProjectedType(),
                includeNested = true,
            )
        }
    }
}
