package com.jimbroze.kbus.generation.processing

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.jimbroze.kbus.contracts.annotations.index.AutoPublishInfo
import com.jimbroze.kbus.contracts.annotations.index.DependencyInfo
import com.jimbroze.kbus.contracts.annotations.index.DependencyType
import com.jimbroze.kbus.contracts.annotations.index.HandlerInfo
import com.jimbroze.kbus.contracts.annotations.index.HandlerType
import com.jimbroze.kbus.generation.processing.autopublish.AutoPublishDefinition
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependency
import com.jimbroze.kbus.generation.processing.dependencies.Dependencies
import com.jimbroze.kbus.generation.processing.dependencies.Dependency
import com.jimbroze.kbus.generation.processing.dependencies.DependencyWithChildren
import com.jimbroze.kbus.generation.processing.dependencies.FunctionalDependency
import com.jimbroze.kbus.generation.processing.dependencies.NonDependency
import com.jimbroze.kbus.generation.processing.dependencies.PropertyDependency
import com.jimbroze.kbus.generation.processing.handlers.CommandHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerKind
import com.jimbroze.kbus.generation.processing.handlers.HandlerData
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.QueryHandlerDefinition
import com.jimbroze.kbus.generation.utility.findArgument
import com.squareup.kotlinpoet.TypeName

class IndexParser(@Suppress("unused") private val logger: KSPLogger) {
    fun createDependencies(dependencyInfoAnnotations: List<KSAnnotation>): Dependencies {
        val dependenciesWithDehydratedChildren = mutableSetOf<DependencyWithDehydratedChildren>()
        for (dependencyInfoAnnotation in dependencyInfoAnnotations) {
            val dependency = createDependency(dependencyInfoAnnotation)
            dependenciesWithDehydratedChildren.add(dependency)
        }

        return IndexDependencies(dependenciesWithDehydratedChildren)
    }

    fun createHandlerFromAnnotation(
        handlerInfoAnnotation: KSAnnotation,
        allDependencies: Set<Dependency>,
    ): HandlerDefinition {
        val allDependenciesBySignature =
            allDependencies.associateBy { dependency -> dependency.signature }
        return createHandler(handlerInfoAnnotation, allDependenciesBySignature)
            ?: error("Could not create a valid handler definition from the provided annotation")
    }

    private fun createDependency(
        dependencyInfoAnnotation: KSAnnotation
    ): DependencyWithDehydratedChildren {
        require(
            dependencyInfoAnnotation.annotationType
                .resolve()
                .declaration
                .qualifiedName
                ?.asString() == DependencyInfo::class.qualifiedName
        ) {
            "Only DependencyInfo annotations can be processed here"
        }

        val signature: String = dependencyInfoAnnotation.findArgument(DependencyInfo::signature)
        val requiresCommandDependencies: Boolean =
            dependencyInfoAnnotation.findArgument(DependencyInfo::requiresCommandDependencies)
        val cannotBeAutoloaded: Boolean =
            dependencyInfoAnnotation.findArgument(DependencyInfo::cannotBeAutoloaded)
        val topLevelDependencies: List<String> =
            dependencyInfoAnnotation.findArgument(DependencyInfo::topLevelDependencies)
        val typeOfDependency: DependencyType =
            dependencyInfoAnnotation.findArgument(DependencyInfo::dependencyType)

        val dependencyTypeName = TypeResolver.resolve(signature)

        val metadata =
            createDependency(typeOfDependency, dependencyTypeName, requiresCommandDependencies)

        return DependencyWithDehydratedChildren(metadata, topLevelDependencies, cannotBeAutoloaded)
    }

    private fun createHandler(
        handlerInfoAnnotation: KSAnnotation,
        allDependenciesBySignature: Map<String, Dependency>,
    ): HandlerDefinition? {
        require(
            handlerInfoAnnotation.annotationType.resolve().declaration.qualifiedName?.asString() ==
                HandlerInfo::class.qualifiedName
        ) {
            "Only HandlerInfo annotations can be processed here"
        }

        val messageClassSignature: String =
            handlerInfoAnnotation.findArgument(HandlerInfo::messageClass)
        val handlerClassSignature: String =
            handlerInfoAnnotation.findArgument(HandlerInfo::handlerClass)
        val returnTypeSignature: String =
            handlerInfoAnnotation.findArgument(HandlerInfo::returnType)
        val topLevelDependenciesSignatures: List<String> =
            handlerInfoAnnotation.findArgument(HandlerInfo::topLevelDependencies)
        val typeOfHandler: HandlerType =
            handlerInfoAnnotation.findArgument(HandlerInfo::handlerType)

        val messageClass = TypeResolver.resolveClassName(messageClassSignature)
        val handlerClass = TypeResolver.resolveClassName(handlerClassSignature)
        val returnType = TypeResolver.resolve(returnTypeSignature)

        val topLevelDependencies =
            topLevelDependenciesSignatures.map { allDependenciesBySignature.getValue(it) }

        val handlerData = HandlerData(handlerClass, messageClass, returnType, topLevelDependencies)

        return createHandler(typeOfHandler, handlerData, logger)
    }

    fun createAutoPublishDefinitions(
        autoPublishInfoAnnotations: List<KSAnnotation>
    ): List<AutoPublishDefinition> {
        return autoPublishInfoAnnotations.map { autoPublishInfoAnnotation ->
            val integrationEventClassSignature: String =
                autoPublishInfoAnnotation.findArgument(AutoPublishInfo::integrationEventClass)
            val domainEventClassSignature: String =
                autoPublishInfoAnnotation.findArgument(AutoPublishInfo::domainEventClass)

            AutoPublishDefinition(
                TypeResolver.resolveClassName(integrationEventClassSignature),
                TypeResolver.resolveClassName(domainEventClassSignature),
            )
        }
    }
}

private data class DependencyWithDehydratedChildren(
    val metadata: Dependency,
    val topLevelDependencySignatures: List<String>,
    val cannotBeAutoloaded: Boolean,
) {
    fun withHydratedChildren(topLevelDependencies: List<Dependency>): DependencyWithChildren {
        return DependencyWithChildren(metadata, topLevelDependencies, cannotBeAutoloaded)
    }
}

private class IndexDependencies(dependencies: Set<DependencyWithDehydratedChildren>) :
    Dependencies {
    private val allDependenciesMetadata = dependencies.map { it.metadata }
    override val topLevelDependencies = allDependenciesMetadata

    private val dependenciesBySignature = allDependenciesMetadata.associateBy { it.signature }
    override val allDependencies =
        dependencies
            .map { dependency ->
                val topLevelDependencies =
                    dependency.topLevelDependencySignatures.map { topLevelDependencySignature ->
                        dependenciesBySignature.getValue(topLevelDependencySignature)
                    }
                dependency.withHydratedChildren(topLevelDependencies)
            }
            .toSet()
}

private fun createDependency(
    dependencyType: DependencyType,
    typeRef: TypeName,
    requiresCommandDependencies: Boolean,
): Dependency {
    return when (dependencyType) {
        DependencyType.PROPERTY -> PropertyDependency(typeRef)
        DependencyType.FUNCTIONAL -> FunctionalDependency(typeRef, requiresCommandDependencies)
        DependencyType.COMMAND -> CommandDependency(typeRef)
        DependencyType.NON_DEPENDENCY -> NonDependency(typeRef)
    }
}

private fun createHandler(
    typeOfHandler: HandlerType,
    handlerData: HandlerData,
    logger: KSPLogger,
): HandlerDefinition? {
    return when (typeOfHandler) {
        HandlerType.COMMAND -> CommandHandlerDefinition(handlerData)
        HandlerType.QUERY -> QueryHandlerDefinition.create(handlerData, logger, null)
        HandlerType.DOMAIN_EVENT -> EventHandlerDefinition(handlerData, EventHandlerKind.DOMAIN)
        HandlerType.INTEGRATION_EVENT ->
            EventHandlerDefinition(handlerData, EventHandlerKind.INTEGRATION)
    }
}
