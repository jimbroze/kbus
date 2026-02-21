package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class HandlersInterfaceGenerator(
    private val codeGenerator: CodeGenerator,
    @Suppress("unused") private val logger: KSPLogger,
    private val handlerInterfaceName: String,
    private val combinedInterfaceName: String,
    private val packagePath: String,
) {
    fun generateCombinedInterface(parents: Set<KSClassDeclaration>) {
        val interfaceBuilder = TypeSpec.interfaceBuilder(combinedInterfaceName)

        for (parent in parents) {
            interfaceBuilder.addSuperinterface(parent.asStarProjectedType().toTypeName())
        }

        val file = FileSpec.builder(packagePath, combinedInterfaceName)
        file.addType(interfaceBuilder.build())

        file.build().writeTo(codeGenerator, Dependencies(true))
    }

    fun generateInterface(handlers: Set<HandlerDefinition>) {
        val interfaceBuilder = TypeSpec.interfaceBuilder(handlerInterfaceName)

        handlers.forEach { addHandlerDefinition(interfaceBuilder, it) }

        val file = FileSpec.builder(packagePath, handlerInterfaceName)
        file.addType(interfaceBuilder.build())
        file.build().writeTo(codeGenerator, Dependencies(true))
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
