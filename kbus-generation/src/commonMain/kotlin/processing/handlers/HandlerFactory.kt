package com.jimbroze.kbus.generation.processing.handlers

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeReference
import com.jimbroze.kbus.api.annotations.index.RequiredDependencies
import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.api.messages.event.EventHandler
import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import com.jimbroze.kbus.api.messages.query.QueryHandler
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import com.jimbroze.kbus.generation.processing.dependencies.Dependency
import com.jimbroze.kbus.generation.processing.dependencies.DependencyFactory
import com.jimbroze.kbus.generation.utility.extendsType
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

class HandlerFactory(
    private val logger: KSPLogger,
    val dependencyFactory: DependencyFactory,
    /**
     * This Gradle module's bounded context identity (`kbus.boundedContextIdentity`), stamped onto
     * every handler it produces. Empty when unassigned.
     */
    private val boundedContextIdentity: String = "",
) {
    // TODO make more polymorphic. Find name of type args?
    fun createHandler(
        handlerClass: KSClassDeclaration,
        constructorDependencies: List<Dependency>,
    ): HandlerDefinition? {
        val handlerBaseClassReference = findBaseClass(handlerClass) ?: return null
        val handlerBaseClass = handlerBaseClassReference.resolve().declaration as KSClassDeclaration
        val baseClassTypeArgs: List<KSTypeArgument> =
            handlerBaseClassReference.element!!.typeArguments

        return if (isEventHandler(handlerBaseClass)) {
            createEventHandlerDefinition(handlerClass, baseClassTypeArgs, constructorDependencies)
        } else {
            createCommandOrQueryHandlerDefinition(
                handlerClass,
                handlerBaseClass,
                handlerBaseClassReference,
                baseClassTypeArgs,
                constructorDependencies,
            )
        }
    }

    private fun createEventHandlerDefinition(
        handlerClass: KSClassDeclaration,
        baseClassTypeArgs: List<KSTypeArgument>,
        constructorDependencies: List<Dependency>,
    ): EventHandlerDefinition? {
        val messageClass =
            baseClassTypeArgs[0].type?.resolve()?.declaration as? KSClassDeclaration
                ?: error("Event type argument missing or invalid")
        val kind = resolveEventKind(messageClass)
        val definition =
            EventHandlerDefinition(
                HandlerData(
                    handlerClass.toClassName(),
                    messageClass.toClassName(),
                    UNIT,
                    constructorDependencies,
                    boundedContextIdentity,
                ),
                kind,
            )

        val isUsable =
            (kind != EventHandlerKind.DOMAIN || isDomainEventHandler(handlerClass, messageClass)) &&
                !hasCommandOnlyDependency(handlerClass, definition)

        return definition.takeIf { isUsable }
    }

    /**
     * An event is dispatched outside any one command's execution, so nothing can supply a handler
     * with what only a command's own invocation holds.
     */
    private fun hasCommandOnlyDependency(
        handlerClass: KSClassDeclaration,
        definition: EventHandlerDefinition,
    ): Boolean {
        if (definition.requiredDependencies != RequiredDependencies.COMMAND) return false
        val commandOnlyDependencies =
            definition.handlerData.topLevelDependencies
                .filter { it.requiredDependencies == RequiredDependencies.COMMAND }
                .joinToString(", ") { it.signature }
        logger.error(
            "Event handler ${handlerClass.qualifiedName?.asString()} depends on " +
                "$commandOnlyDependencies, which only a command's own invocation can supply.",
            handlerClass,
        )
        return true
    }

    private fun isDomainEventHandler(
        handlerClass: KSClassDeclaration,
        messageClass: KSClassDeclaration,
    ): Boolean {
        if (handlerClass.extendsType(DomainEventHandler::class.qualifiedName!!)) return true
        logger.error(
            "Handler ${handlerClass.qualifiedName?.asString()} handles the domain event " +
                "${messageClass.qualifiedName?.asString()}, so it must extend DomainEventHandler " +
                "rather than implement EventHandler directly. Either extend DomainEventHandler, " +
                "or make the event an IntegrationEvent.",
            handlerClass,
        )
        return false
    }

    private fun resolveEventKind(messageClass: KSClassDeclaration): EventHandlerKind {
        return when {
            messageClass.extendsType(DomainEvent::class.qualifiedName!!) -> EventHandlerKind.DOMAIN
            messageClass.extendsType(IntegrationEvent::class.qualifiedName!!) ->
                EventHandlerKind.INTEGRATION
            else ->
                error(
                    "Event ${messageClass.qualifiedName?.asString()} must extend " +
                        "DomainEvent or IntegrationEvent"
                )
        }
    }

    private fun createCommandOrQueryHandlerDefinition(
        handlerClass: KSClassDeclaration,
        handlerBaseClass: KSClassDeclaration,
        handlerBaseClassReference: KSTypeReference,
        baseClassTypeArgs: List<KSTypeArgument>,
        constructorDependencies: List<Dependency>,
    ): HandlerDefinition? {
        if (baseClassTypeArgs.size < 2) {
            val baseClassName =
                handlerBaseClassReference.resolve().declaration.qualifiedName?.asString()
                    ?: handlerBaseClassReference.toString()
            error(
                "Handler base class must have at least two type arguments (message and return type) " +
                    "handler: ${handlerClass.qualifiedName?.asString() ?: handlerClass.simpleName.asString()}; " +
                    "found ${baseClassTypeArgs.size} on base $baseClassName"
            )
        }

        val messageClass =
            baseClassTypeArgs[0].type?.resolve()?.declaration as? KSClassDeclaration
                ?: error("Message type argument is missing or invalid")

        val returnType =
            baseClassTypeArgs[1].type ?: error("Return type argument is missing or invalid")

        return createHandler(
            handlerBaseClass,
            HandlerData(
                handlerClass.toClassName(),
                messageClass.toClassName(),
                returnType.toTypeName(),
                constructorDependencies,
                boundedContextIdentity,
            ),
            logger,
        )
    }

    /**
     * A command handler's message and return types, taken from the base class it extends. Nothing
     * here reads the handler's constructor, so this answers for a handler whose dependencies cannot
     * yet be resolved.
     */
    fun createCommandHandlerSignature(handlerClass: KSClassDeclaration): CommandHandlerDefinition? {
        val handlerBaseClassReference = findBaseClass(handlerClass)
        val isCommandHandler =
            handlerBaseClassReference?.resolve()?.declaration?.qualifiedName?.asString() ==
                CommandHandler::class.qualifiedName

        val baseClassTypeArgs =
            handlerBaseClassReference?.element?.typeArguments?.takeIf {
                isCommandHandler && it.size >= 2
            }
        val messageClass =
            baseClassTypeArgs?.first()?.type?.resolve()?.declaration as? KSClassDeclaration
        val returnType = baseClassTypeArgs?.get(1)?.type

        return if (messageClass == null || returnType == null) null
        else
            CommandHandlerDefinition(
                HandlerData(
                    handlerClass.toClassName(),
                    messageClass.toClassName(),
                    returnType.toTypeName(),
                    emptyList(),
                    boundedContextIdentity,
                )
            )
    }

    private fun findBaseClass(classDeclaration: KSClassDeclaration): KSTypeReference? {
        val classSuperType =
            classDeclaration.superTypes.firstOrNull {
                (it.resolve().declaration as? KSClassDeclaration)?.classKind == ClassKind.CLASS
            }
        if (classSuperType != null) return classSuperType

        return classDeclaration.superTypes.firstOrNull { superType ->
            val decl =
                superType.resolve().declaration as? KSClassDeclaration ?: return@firstOrNull false
            decl.classKind == ClassKind.INTERFACE && isEventHandler(decl)
        }
    }

    private fun isEventHandler(decl: KSClassDeclaration): Boolean {
        if (decl.qualifiedName?.asString() == EventHandler::class.qualifiedName) return true
        return decl.superTypes.any {
            isEventHandler(it.resolve().declaration as? KSClassDeclaration ?: return@any false)
        }
    }
}

fun createHandler(
    handlerBaseClass: KSClassDeclaration,
    handlerData: HandlerData,
    logger: KSPLogger,
): HandlerDefinition? {
    return when (handlerBaseClass.qualifiedName!!.asString()) {
        CommandHandler::class.qualifiedName -> CommandHandlerDefinition(handlerData)
        QueryHandler::class.qualifiedName ->
            QueryHandlerDefinition.create(handlerData, logger, handlerBaseClass)
        EventHandler::class.qualifiedName ->
            EventHandlerDefinition(handlerData, EventHandlerKind.DOMAIN)
        else -> null
    }
}
