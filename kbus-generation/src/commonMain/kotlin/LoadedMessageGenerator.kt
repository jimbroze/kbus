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
    val packageName: String,
    val loadedClassName: String,
) {
    val loadedMessageName = "$packageName.$loadedClassName"
}

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

        val message = messageDefinition.message
        val handler: KSClassDeclaration = handlerClass

        // TODO test for different packages?
        val packageName = handler.packageName.asString()
        val messageClassName = message.simpleName.asString()
        val loadedClassName = "${messageClassName}Loaded"

        val loadedHandlerDefinition =
            LoadedHandlerDefinition(messageDefinition, packageName, loadedClassName)

        @Suppress("SpreadOperator")
        val file =
            codeGenerator.createNewFile(
                Dependencies(
                    true,
                    *listOfNotNull(handler.containingFile, message.containingFile).toTypedArray(),
                ),
                packageName,
                loadedClassName,
            )
        file.write(generateHandlerCode(loadedHandlerDefinition).toString().toByteArray())
        file.close()

        return loadedHandlerDefinition
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

    private fun generateHandlerCode(
        loadedHandlerDefinition: LoadedHandlerDefinition
    ): StringBuilder {
        val messageClassName =
            loadedHandlerDefinition.handlerDefinition.message.simpleName.asString()
        val handlerClassName =
            loadedHandlerDefinition.handlerDefinition.handler.simpleName.asString()
        val loadedClassName = loadedHandlerDefinition.loadedClassName

        val messageTypeLowercase =
            loadedHandlerDefinition.handlerDefinition.messageBaseClass.simpleName
                .toString()
                .lowercase()

        val messageConstructorDependencies =
            loadedHandlerDefinition.handlerDefinition.message.primaryConstructor?.parameters?.map {
                Dependency.fromParameter(it, useParamName = true)
            } ?: emptyList()
        val loadedMessageConstructorParams =
            messageConstructorDependencies.joinToString(", ") { dep ->
                "${dep.name}: ${dep.getTypeWithArgs()}"
            }
        val messageConstructorArgs =
            messageConstructorDependencies.joinToString(", ") { dep -> dep.name }

        val packageName = loadedHandlerDefinition.packageName

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

        return fileText
    }
}

fun findBaseClass(classDeclaration: KSClassDeclaration): KSClassDeclaration? {
    return classDeclaration.superTypes
        .mapNotNull { superType -> superType.resolve().declaration as? KSClassDeclaration }
        .find { it.classKind == ClassKind.CLASS }
}
