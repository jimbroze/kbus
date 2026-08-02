package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo

class HandlersInterfaceGenerator(
    private val codeGenerator: CodeGenerator,
    @Suppress("unused") private val logger: KSPLogger,
    private val handlerInterfaceName: String,
    private val packagePath: String,
) {
    /** One interface per bounded context, matching that context's own handler factory. */
    fun generateInterfaces(handlers: Set<HandlerDefinition>, sourceFiles: List<KSFile>) {
        val handlersByContext = handlers.groupBy { contextOf(it) }
        contextIdentities(handlers).forEach { context ->
            generateInterface(context, handlersByContext[context].orEmpty().toSet(), sourceFiles)
        }
    }

    private fun generateInterface(
        context: String,
        handlers: Set<HandlerDefinition>,
        sourceFiles: List<KSFile>,
    ) {
        val interfaceName = contextClassPrefix(context) + handlerInterfaceName
        val interfaceBuilder = TypeSpec.interfaceBuilder(interfaceName)

        handlers.forEach { addHandlerDefinition(interfaceBuilder, it) }

        val file = FileSpec.builder(packagePath, interfaceName)
        file.addType(interfaceBuilder.build())
        file
            .build()
            .writeTo(codeGenerator, Dependencies(true, sources = sourceFiles.toTypedArray()))
    }

    private fun addHandlerDefinition(
        interfaceBuilder: TypeSpec.Builder,
        handler: HandlerDefinition,
    ) {
        val functionBuilder =
            FunSpec.builder(handler.handlerData.nameAsDependency)
                .addModifiers(KModifier.ABSTRACT)
                .returns(handler.handlerData.handlerClass)

        for (functionParameter in handler.functionParameters) {
            functionBuilder.addParameter(functionParameter.name, functionParameter.typeRef)
        }

        interfaceBuilder.addFunction(functionBuilder.build())
    }
}
