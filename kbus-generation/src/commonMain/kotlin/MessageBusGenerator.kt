package com.jimbroze.kbus.generation

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.jimbroze.kbus.core.MessageBus
import com.jimbroze.kbus.core.Middleware
import kotlin.text.StringBuilder

class MessageBusGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val busClassName: String,
) {

    fun generate(packagePath: String, loaderName: String, handlers: Set<LoadedHandlerDefinition>) {
        logger.info("Generating CompileTimeLoadedMessageBus")

        val fileText = StringBuilder()

        fileText.appendLine("package $packagePath")
        fileText.appendLine()
        fileText.append(generateBusClassCode(loaderName, handlers))

        val file = codeGenerator.createNewFile(Dependencies(true), packagePath, busClassName)
        file.write(fileText.toString().toByteArray())
        file.close()
    }

    private fun generateBusClassCode(
        loaderName: String,
        handlers: Set<LoadedHandlerDefinition>,
    ): StringBuilder {
        // TODO use MessageBus constructor for type safety? Replace pre-written class instead?
        val superClassName = MessageBus::class.qualifiedName!!
        val middlewareClassName = Middleware::class.qualifiedName!!

        val busClassCode = StringBuilder()
        busClassCode.appendLine("class $busClassName(")
        busClassCode.appendLine("    middleware: List<$middlewareClassName>,")
        busClassCode.appendLine("    private val loader: $loaderName,")
        busClassCode.appendLine(") : $superClassName(middleware) {")

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
