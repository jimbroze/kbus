package com.jimbroze.kbus.generation.processing.handlers

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeReference
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.generation.processing.dependencies.Dependency
import com.jimbroze.kbus.generation.processing.dependencies.DependencyFactory
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

class HandlerFactory(
    @Suppress("unused") private val logger: KSPLogger,
    val dependencyFactory: DependencyFactory,
) {
    fun createHandler(
        handlerClass: KSClassDeclaration,
        constructorDependencies: List<Dependency>,
    ): HandlerDefinition? {
        val handlerBaseClassReference = findBaseClass(handlerClass) ?: return null

        val baseClassTypeArgs: List<KSTypeArgument> =
            handlerBaseClassReference.element!!.typeArguments
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
            handlerBaseClassReference.resolve().declaration as KSClassDeclaration,
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
        return classDeclaration.superTypes.firstOrNull {
            (it.resolve().declaration as? KSClassDeclaration)!!.classKind == ClassKind.CLASS
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
        else -> null
    }
}
