package com.jimbroze.kbus.generation

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import kotlin.text.StringBuilder

class MessageBusGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val packageName: String,
    private val loaderClassName: String,
) {
    companion object {
        const val BUS_CLASS_NAME = "CompileTimeLoadedMessageBus"
    }

    fun generate(handlers: Set<LoadedHandlerDefinition>) {
        logger.info("Generating CompileTimeLoadedMessageBus")

        val fileText = StringBuilder()

        fileText.appendLine("package $packageName")
        fileText.appendLine()
        fileText.append(generateBusClassCode(handlers))

        val file = codeGenerator.createNewFile(Dependencies(true), packageName, BUS_CLASS_NAME)
        file.write(fileText.toString().toByteArray())
        file.close()
    }

    private fun generateBusClassCode(handlers: Set<LoadedHandlerDefinition>): StringBuilder {
        // TODO use MessageBus constructor for type safety? Replace pre-written class instead?

        val busClassCode = StringBuilder()
        busClassCode.appendLine("class $BUS_CLASS_NAME(")
        busClassCode.appendLine("    middleware: List<Middleware>,")
        busClassCode.appendLine("    private val loader: $loaderClassName,")
        busClassCode.appendLine(") : MessageBus(middleware) {")

        for (handler in handlers) {
            busClassCode.append(addMethodToBusClass(handler))
        }

        busClassCode.appendLine("}")

        return busClassCode
    }

    private fun addMethodToBusClass(classDefinition: LoadedHandlerDefinition): StringBuilder {
        val busMethodCode = StringBuilder()

        val messageType = classDefinition.handlerDefinition.messageBaseClass.simpleName!!
        val messageTypeLowercase = messageType.lowercase()

        val handlerName =
            classDefinition.handlerDefinition.handler.simpleName.asString().replaceFirstChar {
                it.lowercase()
            }
        val loadedMessageName = classDefinition.loadedMessageName

        busMethodCode.appendLine("    suspend fun execute(loaded$messageType: $loadedMessageName)")
        busMethodCode.appendLine(
            "        = this.execute(loaded$messageType.$messageTypeLowercase, this.loader.$handlerName)"
        )

        return busMethodCode
    }
}
