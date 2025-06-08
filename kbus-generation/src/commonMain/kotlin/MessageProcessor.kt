package com.jimbroze.kbus.generation

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.validate
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.jimbroze.kbus.annotations.Load

@Suppress("unused")
class MessageProcessor(
    private val logger: KSPLogger,
    private val dependencyFactory: DependencyFactory,
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
            rootPackageName.addName(symbol.accept(LoadVisitor(), dependencies))
        }

        if (dependencies.isEmpty()) return

        val generatedPackageName = "$rootPackageName.generated"

        dependencyLoaderGenerator.generateLoaderInterface(generatedPackageName, dependencies)
    }

    inner class LoadVisitor : KSDefaultVisitor<MutableSet<NestedDependency>, KSName>() {
        override fun defaultHandler(node: KSNode, data: MutableSet<NestedDependency>): KSName {
            error(
                "Only classes can be annotated with @${Load::class.simpleName}. " +
                    "$node is not a class"
            )
        }

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration,
            data: MutableSet<NestedDependency>,
        ): KSName {
            if (classDeclaration.classKind != ClassKind.CLASS) {
                error(
                    "Only classes can be annotated with @${Load::class.simpleName}. " +
                        "$classDeclaration is a ${classDeclaration.classKind}"
                )
            }

            data.addAll(
                dependencyFactory.generateFrom(
                    classDeclaration.asStarProjectedType(),
                    includeNested = true,
                )
            )

            return classDeclaration.qualifiedName!!
        }
    }
}
