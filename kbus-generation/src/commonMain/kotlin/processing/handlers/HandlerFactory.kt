package com.jimbroze.kbus.generation.processing.handlers

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
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
import com.jimbroze.kbus.generation.utility.typeArgumentsSuppliedTo
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.ksp.toClassName

class HandlerFactory(
    private val logger: KSPLogger,
    val dependencyFactory: DependencyFactory,
    /**
     * This Gradle module's bounded context identity (`kbus.boundedContextIdentity`), stamped onto
     * every handler it produces. Empty when unassigned.
     */
    private val boundedContextIdentity: String = "",
) {
    fun createHandler(
        handlerClass: KSClassDeclaration,
        constructorDependencies: List<Dependency>,
    ): HandlerDefinition? {
        val commandHandlerTypes = handlerClass.typeArgumentsSuppliedTo(COMMAND_HANDLER)
        val queryHandlerTypes = handlerClass.typeArgumentsSuppliedTo(QUERY_HANDLER)
        return when {
            commandHandlerTypes != null ->
                CommandHandlerDefinition(
                    handlerData(handlerClass, commandHandlerTypes, constructorDependencies)
                )
            queryHandlerTypes != null ->
                QueryHandlerDefinition.create(
                    handlerData(handlerClass, queryHandlerTypes, constructorDependencies),
                    logger,
                    handlerClass,
                )
            else -> createEventHandlerDefinition(handlerClass, constructorDependencies)
        }
    }

    private fun createEventHandlerDefinition(
        handlerClass: KSClassDeclaration,
        constructorDependencies: List<Dependency>,
    ): EventHandlerDefinition? {
        val baseClassTypeArgs =
            findEventHandlerBaseClass(handlerClass)?.element?.typeArguments ?: return null
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

    /**
     * A command handler's message and return types, taken from the [CommandHandler] it extends.
     * Nothing here reads the handler's constructor, so this answers for a handler whose
     * dependencies cannot yet be resolved.
     */
    fun createCommandHandlerSignature(handlerClass: KSClassDeclaration): CommandHandlerDefinition? =
        handlerClass.typeArgumentsSuppliedTo(COMMAND_HANDLER)?.let {
            CommandHandlerDefinition(handlerData(handlerClass, it, emptyList()))
        }

    private fun handlerData(
        handlerClass: KSClassDeclaration,
        messageAndReturnTypes: List<TypeName>,
        constructorDependencies: List<Dependency>,
    ): HandlerData {
        val (messageType, returnType) = messageAndReturnTypes
        val messageClass =
            (messageType as? ParameterizedTypeName)?.rawType
                ?: messageType as? ClassName
                ?: error(
                    "Handler ${handlerClass.qualifiedName?.asString()} handles $messageType, " +
                        "which is not a class"
                )
        return HandlerData(
            handlerClass.toClassName(),
            messageClass,
            returnType,
            constructorDependencies,
            boundedContextIdentity,
        )
    }

    private fun findEventHandlerBaseClass(handlerClass: KSClassDeclaration): KSTypeReference? {
        val eventHandlerSuperTypes =
            handlerClass.superTypes.filter { superType ->
                val superClass = superType.resolve().declaration as? KSClassDeclaration
                superClass != null && isEventHandler(superClass)
            }
        return eventHandlerSuperTypes.firstOrNull {
            (it.resolve().declaration as KSClassDeclaration).classKind == ClassKind.CLASS
        } ?: eventHandlerSuperTypes.firstOrNull()
    }

    private fun isEventHandler(decl: KSClassDeclaration): Boolean {
        if (decl.qualifiedName?.asString() == EventHandler::class.qualifiedName) return true
        return decl.superTypes.any {
            isEventHandler(it.resolve().declaration as? KSClassDeclaration ?: return@any false)
        }
    }
}

private val COMMAND_HANDLER = CommandHandler::class.qualifiedName!!
private val QUERY_HANDLER = QueryHandler::class.qualifiedName!!
