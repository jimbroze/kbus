package com.jimbroze.kbus.generation.processing.handlers

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeReference
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.domain.DomainEvent
import com.jimbroze.kbus.generation.processing.dependencies.Dependency
import com.jimbroze.kbus.generation.processing.dependencies.DependencyFactory
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

class HandlerFactory(
    @Suppress("unused") private val logger: KSPLogger,
    val dependencyFactory: DependencyFactory,
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
    ): EventHandlerDefinition {
        val messageClass =
            baseClassTypeArgs[0].type?.resolve()?.declaration as? KSClassDeclaration
                ?: error("Event type argument missing or invalid")
        val kind = resolveEventKind(messageClass)
        return EventHandlerDefinition(
            HandlerData(
                handlerClass.toClassName(),
                messageClass.toClassName(),
                UNIT,
                constructorDependencies,
            ),
            kind,
        )
    }

    private fun resolveEventKind(messageClass: KSClassDeclaration): EventHandlerKind {
        return when {
            extendsType(messageClass, DomainEvent::class.qualifiedName!!) -> EventHandlerKind.DOMAIN
            extendsType(messageClass, IntegrationEvent::class.qualifiedName!!) ->
                EventHandlerKind.INTEGRATION
            else ->
                error(
                    "Event ${messageClass.qualifiedName?.asString()} must extend " +
                        "DomainEvent or IntegrationEvent"
                )
        }
    }

    private fun extendsType(decl: KSClassDeclaration, qualifiedName: String): Boolean {
        if (decl.qualifiedName?.asString() == qualifiedName) return true
        return decl.superTypes.any { superType ->
            val superDecl = superType.resolve().declaration as? KSClassDeclaration
            superDecl != null && extendsType(superDecl, qualifiedName)
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
            ),
            logger,
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
