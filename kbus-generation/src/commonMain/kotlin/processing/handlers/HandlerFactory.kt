package com.jimbroze.kbus.generation.processing.handlers

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeReference
import com.jimbroze.kbus.generation.processing.dependencies.Dependency
import com.jimbroze.kbus.generation.processing.dependencies.DependencyFactory

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

        return HandlerDefinition.create(
            handlerBaseClassReference.resolve().declaration as KSClassDeclaration,
            HandlerData(
                nameForDependency(handlerClass),
                handlerClass,
                messageClass,
                returnType,
                constructorDependencies,
            ),
        )
    }

    private fun findBaseClass(classDeclaration: KSClassDeclaration): KSTypeReference? {
        return classDeclaration.superTypes.firstOrNull {
            (it.resolve().declaration as? KSClassDeclaration)!!.classKind == ClassKind.CLASS
        }
    }
}

fun nameForDependency(declaration: KSDeclaration): String {
    return declaration.simpleName.asString().replaceFirstChar { it.lowercase() }
}
