package com.jimbroze.kbus.generation.processing.autopublish

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.jimbroze.kbus.core.messages.event.AutoPublishesFrom
import com.squareup.kotlinpoet.ksp.toClassName

private sealed interface AutoPublishSearchResult {
    data object NotImplemented : AutoPublishSearchResult

    data object Unresolvable : AutoPublishSearchResult

    data class Resolved(val domainEventClass: KSClassDeclaration) : AutoPublishSearchResult
}

/**
 * Discovers whether an [com.jimbroze.kbus.contracts.messages.event.Event]'s companion object opts
 * into generated auto-publish registrations by implementing
 * [AutoPublishesFrom][com.jimbroze.kbus.core.messages.event.AutoPublishesFrom], directly or via
 * intermediate (possibly generic) interfaces.
 */
class AutoPublishFactory(private val logger: KSPLogger) {
    fun create(classDeclaration: KSClassDeclaration): AutoPublishDefinition? {
        val companion =
            classDeclaration.declarations.filterIsInstance<KSClassDeclaration>().firstOrNull {
                it.isCompanionObject
            } ?: return null

        return when (val result = search(companion, emptyMap())) {
            is AutoPublishSearchResult.NotImplemented -> null
            is AutoPublishSearchResult.Unresolvable -> {
                logger.error(
                    "Companion of ${classDeclaration.qualifiedName?.asString()} implements " +
                        "${AutoPublishesFrom::class.simpleName} but its domain event type " +
                        "argument could not be resolved",
                    companion,
                )
                null
            }

            is AutoPublishSearchResult.Resolved ->
                AutoPublishDefinition(
                    classDeclaration.toClassName(),
                    result.domainEventClass.toClassName(),
                )
        }
    }

    private fun search(
        declaration: KSClassDeclaration,
        substitution: Map<String, KSType>,
    ): AutoPublishSearchResult =
        declaration.superTypes.firstNotNullOfOrNull { superTypeRef ->
            searchSuperType(superTypeRef, substitution)
        } ?: AutoPublishSearchResult.NotImplemented

    private fun searchSuperType(
        superTypeRef: KSTypeReference,
        substitution: Map<String, KSType>,
    ): AutoPublishSearchResult? {
        val resolvedSuperType = superTypeRef.resolve()
        val superDeclaration = resolvedSuperType.declaration as? KSClassDeclaration ?: return null

        return if (
            superDeclaration.qualifiedName?.asString() == AutoPublishesFrom::class.qualifiedName
        ) {
            resolveDomainEvent(resolvedSuperType, substitution)
        } else {
            val substitutedArgs =
                resolvedSuperType.arguments.map { substituteArgument(it, substitution) }
            val nextSubstitution = buildSubstitution(superDeclaration, substitutedArgs)

            search(superDeclaration, nextSubstitution).takeUnless {
                it is AutoPublishSearchResult.NotImplemented
            }
        }
    }

    private fun resolveDomainEvent(
        resolvedSuperType: KSType,
        substitution: Map<String, KSType>,
    ): AutoPublishSearchResult {
        val domainEventType =
            resolvedSuperType.arguments.getOrNull(0)?.let { substituteArgument(it, substitution) }
        val domainEventDeclaration = domainEventType?.declaration as? KSClassDeclaration

        return if (domainEventDeclaration != null && !domainEventType.isError) {
            AutoPublishSearchResult.Resolved(domainEventDeclaration)
        } else {
            AutoPublishSearchResult.Unresolvable
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
