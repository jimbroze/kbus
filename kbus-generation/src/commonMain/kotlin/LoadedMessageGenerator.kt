package com.jimbroze.kbus.generation

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.jimbroze.kbus.core.Message
import kotlin.reflect.KClass

data class LoadedHandlerDefinition(
    val handlerDefinition: HandlerDefinition,
    val loadedMessageName: String,
)

data class HandlerDefinition(
    val handler: KSClassDeclaration,
    val message: KSClassDeclaration,
    val messageBaseClass: KClass<out Message>,
)

class LoadedMessageGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val loadableMessages: List<KClass<out Message>>,
) {
    fun generateLoadedMessage(handlerClass: KSClassDeclaration): LoadedHandlerDefinition? {
        val messageDefinition = messageForHandler(handlerClass) ?: return null

        return createLoadedMessage(messageDefinition, handlerClass)
    }

    private fun messageForHandler(handlerClass: KSClassDeclaration): HandlerDefinition? {
        val possibleHandleMethods =
            handlerClass.getDeclaredFunctions().filter {
                it.simpleName.asString() == "handle" && it.parameters.count() == 1
            }

        val validHandlerMethods =
            possibleHandleMethods.mapNotNull { isValidHandleMethod(it, handlerClass) }

        return when (validHandlerMethods.count()) {
            1 -> validHandlerMethods.first()
            0 -> null
            else -> {
                logger.error("Multiple valid 'handle' functions found for handler", handlerClass)
                null
            }
        }
    }

    private fun isValidHandleMethod(
        handleFunction: KSFunctionDeclaration,
        handlerClass: KSClassDeclaration,
    ): HandlerDefinition? {
        val messageClass = handleFunction.parameters.first().type.resolve().declaration

        if (messageClass !is KSClassDeclaration) {
            return null
        }

        val messageTypeDeclaration = findBaseClass(messageClass)
        val messageType =
            loadableMessages.find {
                it.qualifiedName == messageTypeDeclaration?.qualifiedName?.asString()
            }

        return messageType?.let { HandlerDefinition(handlerClass, messageClass, it) }
    }

    private fun createLoadedMessage(
        messageDefinition: HandlerDefinition,
        classDeclaration: KSClassDeclaration,
    ): LoadedHandlerDefinition {
        val message = messageDefinition.message
        val handler: KSClassDeclaration = classDeclaration
        val messageTypeLowercase =
            messageDefinition.messageBaseClass.simpleName.toString().lowercase()

        // TODO test for different packages?
        val packageName = handler.containingFile!!.packageName.asString()
        val messageClassName = message.simpleName.asString()
        val handlerClassName = handler.simpleName.asString()
        val loadedClassName = "${messageClassName}Loaded"

        val messageConstructorDependencies =
            message.primaryConstructor?.parameters?.map {
                Dependency.fromParameter(it, useParamName = true)
            } ?: emptyList()

        val loadedMessageConstructorParams =
            messageConstructorDependencies.joinToString(", ") { dep ->
                "${dep.name}: ${dep.getTypeWithArgs()}"
            }
        val messageConstructorArgs =
            messageConstructorDependencies.joinToString(", ") { dep -> dep.name }

        val fileText = StringBuilder()
        fileText.appendLine("package $packageName")
        fileText.appendLine()
        fileText.appendLine("class $loadedClassName($loadedMessageConstructorParams) {")
        fileText.appendLine(
            "    val $messageTypeLowercase = ${messageClassName}(${messageConstructorArgs})"
        )
        fileText.appendLine(
            "    suspend fun handle(handler: $handlerClassName) = handler.handle($messageTypeLowercase)"
        )
        fileText.appendLine("}")

        val file =
            codeGenerator.createNewFile(
                Dependencies(true, handler.containingFile!!, message.containingFile!!),
                packageName,
                loadedClassName,
            )

        file.write(fileText.toString().toByteArray())
        file.close()

        return LoadedHandlerDefinition(messageDefinition, "$packageName.$loadedClassName")
    }
}

fun findBaseClass(classDeclaration: KSClassDeclaration): KSClassDeclaration? {
    return classDeclaration.superTypes
        .mapNotNull { superType -> superType.resolve().declaration as? KSClassDeclaration }
        .find { it.classKind == ClassKind.CLASS }
}
