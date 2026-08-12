package com.jimbroze.kbus.generation.processing.autopublish

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.jimbroze.kbus.contracts.annotations.LoadEventMapper
import com.jimbroze.kbus.core.messages.event.dispatch.IntegrationEventMapper
import com.squareup.kotlinpoet.ksp.toClassName

private sealed interface DomainEventSearchResult {
    data object NotAMapper : DomainEventSearchResult

    data object Unresolvable : DomainEventSearchResult

    data class Resolved(val domainEventClass: KSClassDeclaration) : DomainEventSearchResult
}

/**
 * Reads the domain event a mapper maps from out of its [IntegrationEventMapper] supertype, found
 * directly or through intermediate (possibly generic) interfaces.
 */
class AutoPublishFactory(private val logger: KSPLogger) {
    fun create(mapperDeclaration: KSClassDeclaration): AutoPublishDefinition? =
        when (val result = search(mapperDeclaration, emptyMap())) {
            is DomainEventSearchResult.NotAMapper -> {
                logger.error(
                    "Only ${IntegrationEventMapper::class.simpleName} implementations can be " +
                        "annotated with @${LoadEventMapper::class.simpleName}",
                    mapperDeclaration,
                )
                null
            }

            is DomainEventSearchResult.Unresolvable -> {
                logger.error(
                    "${mapperDeclaration.qualifiedName?.asString()} implements " +
                        "${IntegrationEventMapper::class.simpleName} but its domain event type " +
                        "argument could not be resolved",
                    mapperDeclaration,
                )
                null
            }

            is DomainEventSearchResult.Resolved ->
                AutoPublishDefinition(
                    mapperDeclaration.toClassName(),
                    result.domainEventClass.toClassName(),
                )
        }

    private fun search(
        declaration: KSClassDeclaration,
        substitution: Map<String, KSType>,
    ): DomainEventSearchResult =
        declaration.superTypes.firstNotNullOfOrNull { superTypeRef ->
            searchSuperType(superTypeRef, substitution)
        } ?: DomainEventSearchResult.NotAMapper

    private fun searchSuperType(
        superTypeRef: KSTypeReference,
        substitution: Map<String, KSType>,
    ): DomainEventSearchResult? {
        val resolvedSuperType = superTypeRef.resolve()
        val superDeclaration = resolvedSuperType.declaration as? KSClassDeclaration ?: return null

        return if (
            superDeclaration.qualifiedName?.asString() ==
                IntegrationEventMapper::class.qualifiedName
        ) {
            resolveDomainEvent(resolvedSuperType, substitution)
        } else {
            val substitutedArgs =
                resolvedSuperType.arguments.map { substituteArgument(it, substitution) }
            val nextSubstitution = buildSubstitution(superDeclaration, substitutedArgs)

            search(superDeclaration, nextSubstitution).takeUnless {
                it is DomainEventSearchResult.NotAMapper
            }
        }
    }

    private fun resolveDomainEvent(
        resolvedSuperType: KSType,
        substitution: Map<String, KSType>,
    ): DomainEventSearchResult {
        val domainEventType =
            resolvedSuperType.arguments.getOrNull(0)?.let { substituteArgument(it, substitution) }
        val domainEventDeclaration = domainEventType?.declaration as? KSClassDeclaration

        return if (domainEventDeclaration != null && !domainEventType.isError) {
            DomainEventSearchResult.Resolved(domainEventDeclaration)
        } else {
            DomainEventSearchResult.Unresolvable
        }
    }

    private fun buildSubstitution(
        declaration: KSClassDeclaration,
        args: List<KSType?>,
    ): Map<String, KSType> = buildMap {
        declaration.typeParameters.forEachIndexed { index, typeParam ->
            args.getOrNull(index)?.let { put(typeParam.name.asString(), it) }
        }
    }

    private fun substituteArgument(
        argument: KSTypeArgument,
        substitution: Map<String, KSType>,
    ): KSType? {
        val argType = argument.type?.resolve() ?: return null
        val paramName = (argType.declaration as? KSTypeParameter)?.name?.asString()
        return if (paramName != null) substitution[paramName] else argType
    }
}
