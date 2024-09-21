package com.jimbroze.kbus.generation

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.jimbroze.kbus.core.Message
import kotlin.reflect.KClass

class LoadedMessageGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val loadableMessages: List<KClass<out Message>>
) {
    fun generateLoadedMessage(handlerClass: KSClassDeclaration): LoadedHandlerDefinition? {
        val messageDefinition = messageForHandler(handlerClass) ?: return null

        return createLoadedMessage(messageDefinition, handlerClass)
    }

    private fun messageForHandler(handlerClass: KSClassDeclaration): HandlerDefinition? {
        // TODO improve finding handle function. Need to get override and check that command handler?
        val possibleHandleMethods = handlerClass.getDeclaredFunctions()
            .filter {
                it.simpleName.asString() == "handle"
                        && it.parameters.count() == 1
            }

        var messageClass: KSClassDeclaration? = null
        var messageType: KClass<out Message>? = null
        for (handleFunction in possibleHandleMethods) {
            val messageSubClass = handleFunction
                .parameters.first()
                .type.resolve().declaration

            if (messageSubClass !is KSClassDeclaration) {
                continue
            }

            val messageTypeDeclaration = findBaseClass(messageSubClass) ?: continue
            messageType = loadableMessages
                .find { it.qualifiedName == messageTypeDeclaration.qualifiedName?.asString() } ?: continue

            if (messageClass !== null) {
                logger.error(
                    "Multiple valid 'handle' functions found for handler",
                    handlerClass
                )
                return null
            }

            messageClass = messageSubClass
        }


        if (messageClass == null || messageType == null) {
            logger.error("Message handler must have a valid 'handle' function.", handlerClass)
            return null
        }

        return HandlerDefinition(handlerClass, messageClass, messageType)
    }

    private fun createLoadedMessage(
        messageDefinition: HandlerDefinition,
        classDeclaration: KSClassDeclaration,
    ): LoadedHandlerDefinition {
        val message = messageDefinition.message
        val handler: KSClassDeclaration = classDeclaration
        val messageTypeLowercase = messageDefinition.messageBaseClass.simpleName.toString().lowercase()

        // TODO test for different packages?
        val packageName = handler.containingFile!!.packageName.asString()
        val messageClassName = message.simpleName.asString()
        val handlerClassName = handler.simpleName.asString()
        val loadedClassName = "${messageClassName}Loaded"

        val loadedMessageConstructorParameters = StringBuilder()
        val messageConstructorParameters = StringBuilder()
        var firstParam = true
        //            TODO handler null constructor?
        for (messageParameter in message.primaryConstructor?.parameters!!) {
            val messageParameterNames = getParamNames(messageParameter)
            val name = messageParameterNames.name
            val typeName = messageParameterNames.typeName

            loadedMessageConstructorParameters.append("${if (firstParam) "" else ", "}$name: $typeName")
            messageConstructorParameters.append("${if (firstParam) "" else ", "}$name")

            firstParam = false
        }

        val fileText = StringBuilder()
        fileText.appendLine("package $packageName")
        fileText.appendLine()
        fileText.appendLine("class $loadedClassName($loadedMessageConstructorParameters) {")
        fileText.appendLine("    val $messageTypeLowercase = ${messageClassName}(${messageConstructorParameters})")
        fileText.appendLine("    suspend fun handle(handler: $handlerClassName) = handler.handle($messageTypeLowercase)")
        fileText.appendLine("}")

        val file = codeGenerator.createNewFile(
            Dependencies(true, handler.containingFile!!, message.containingFile!!),
            packageName,
            loadedClassName
        )

        file.write(fileText.toString().toByteArray())
        file.close()

        return LoadedHandlerDefinition(
            messageDefinition,
            "$packageName.$loadedClassName",
        )
    }
}
