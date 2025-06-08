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
        val rootPackageName = RootPackageName()

        for (symbol in symbols) {
            val packageName = symbol.accept(LoadVisitor(), dependencies)
            rootPackageName.addNameOption(packageName)
        }

        if (dependencies.isEmpty()) return

        val generatedPackageName = "$rootPackageName.generated"

        dependencyLoaderGenerator.generateLoaderInterface(generatedPackageName, dependencies)
    }

    inner class LoadVisitor : KSDefaultVisitor<MutableSet<NestedDependency>, String>() {
        override fun defaultHandler(node: KSNode, data: MutableSet<NestedDependency>): String {
            return ""
        }

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration,
            data: MutableSet<NestedDependency>,
        ): String {
            if (classDeclaration.classKind != ClassKind.CLASS) {
                logger.error(
                    "Only classes can be annotated with @${Load::class.simpleName}",
                    classDeclaration,
                )
                return ""
            }

            data.addAll(
                dependencyProcessor.generateFrom(
                    classDeclaration.asStarProjectedType(),
                    includeNested = true,
                )
            )

            return classDeclaration.packageName.asString()
        }
    }
}
