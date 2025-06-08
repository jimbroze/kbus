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
import com.jimbroze.kbus.annotations.GenerateContainer
import com.jimbroze.kbus.annotations.Load

class ContainerProcessor(
    private val logger: KSPLogger,
    private val dependencyProcessor: DependencyProcessor,
    private val dependencyLoaderGenerator: ContainerGenerator,
    private val loadedMessageGenerator: LoadedMessageGenerator,
    private val busGenerator: MessageBusGenerator,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val containerInterfaces =
            resolver.getSymbolsWithAnnotation(GenerateContainer::class.qualifiedName.toString())

        processContainerInterfaces(containerInterfaces)

        return containerInterfaces.filterNot { it.validate() }.toList()
    }

    private fun processContainerInterfaces(symbols: Sequence<KSAnnotated>) {
        val loaderInterfaces = mutableSetOf<String>()
        val dependencies = mutableSetOf<NestedDependency>()

        for (symbol in symbols) {
            loaderInterfaces.add(symbol.accept(ContainerVisitor(), dependencies))
        }

        val loadedMessages = mutableSetOf<LoadedHandlerDefinition>()
        for (dependency in dependencies) {
            validateNoDuplicates(dependencies, dependency)

            dependency.declaration.accept(DependencyVisitor(), Unit)?.let { loadedMessages.add(it) }
        }

        if (loaderInterfaces.isEmpty() || dependencies.isEmpty()) return

        val loaderName =
            dependencyLoaderGenerator.generateLoaderClass(loaderInterfaces, dependencies)

        busGenerator.generate(loaderName, loadedMessages)
    }

    private fun validateNoDuplicates(
        allDependencies: MutableSet<NestedDependency>,
        dependency: NestedDependency,
    ) {
        val matches = allDependencies.filter { other -> dependency.isDuplicateOf(other) }
        if (matches.isNotEmpty()) {
            val dependencyName = dependency.declaration.simpleName.asString()
            logger.error(
                "Tried to generate multiple dependencies for $dependencyName",
                dependency.declaration,
            )
        }
    }

    inner class DependencyVisitor : KSDefaultVisitor<Unit, LoadedHandlerDefinition?>() {
        override fun defaultHandler(node: KSNode, data: Unit): LoadedHandlerDefinition? {
            return null
        }

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration,
            data: Unit,
        ): LoadedHandlerDefinition? {
            return if (isLoadableHandler(classDeclaration)) {
                loadedMessageGenerator.generateLoadedMessage(classDeclaration)
            } else {
                null
            }
        }

        private fun isLoadableHandler(classDeclaration: KSClassDeclaration): Boolean {
            val potentialLoadAnnotations =
                classDeclaration.annotations.filter {
                    it.shortName.asString() == Load::class.simpleName
                }

            return Load::class.qualifiedName in
                potentialLoadAnnotations.map {
                    it.annotationType.resolve().declaration.qualifiedName?.asString()
                }
        }
    }

    inner class ContainerVisitor : KSDefaultVisitor<MutableSet<NestedDependency>, String>() {
        override fun defaultHandler(node: KSNode, data: MutableSet<NestedDependency>): String {
            return ""
        }

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration,
            data: MutableSet<NestedDependency>,
        ): String {
            if (classDeclaration.classKind != ClassKind.INTERFACE) {
                logger.error(
                    "Only interfaces can be annotated with @${GenerateContainer::class.simpleName}",
                    classDeclaration,
                )
                return ""
            }

            data.addAll(
                dependencyProcessor.generateFrom(
                    classDeclaration.getAllProperties(),
                    includeNested = false,
                )
            )

            return classDeclaration.qualifiedName!!.asString()
        }
    }
}
