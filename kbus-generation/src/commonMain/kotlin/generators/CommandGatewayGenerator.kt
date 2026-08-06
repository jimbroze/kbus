package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.jimbroze.kbus.contracts.messages.command.CommandGateway
import com.jimbroze.kbus.core.bus.IMessageBus
import com.jimbroze.kbus.generation.processing.handlers.CommandHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.writeTo

private const val BUS_CONSTRUCTOR_PARAMETER = "bus"

/**
 * Generates one [CommandGateway] implementation per command that has a handler, so that handing a
 * module the ability to send a command is a compile-time claim that something can handle it.
 */
class CommandGatewayGenerator(
    private val codeGenerator: CodeGenerator,
    @Suppress("unused") private val logger: KSPLogger,
    private val gatewayClassSuffix: String,
    private val packagePath: String,
) {
    fun generateGateways(handlers: Set<HandlerDefinition>, sourceFiles: List<KSFile>) {
        handlers.filterIsInstance<CommandHandlerDefinition>().forEach { command ->
            val commandClass = command.handlerData.messageClass
            val className = commandClass.simpleName + gatewayClassSuffix

            val type =
                TypeSpec.classBuilder(className)
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter(BUS_CONSTRUCTOR_PARAMETER, IMessageBus::class)
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder(
                                BUS_CONSTRUCTOR_PARAMETER,
                                IMessageBus::class,
                                KModifier.PRIVATE,
                            )
                            .initializer(BUS_CONSTRUCTOR_PARAMETER)
                            .build()
                    )
                    .addSuperinterface(
                        CommandGateway::class.asClassName()
                            .parameterizedBy(commandClass, command.handlerData.returnType)
                    )
                    .addFunction(
                        FunSpec.builder("execute")
                            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                            .addParameter("command", commandClass)
                            .returns(command.handlerData.returnType)
                            .addStatement("return %L.execute(command)", BUS_CONSTRUCTOR_PARAMETER)
                            .build()
                    )
                    .build()

            FileSpec.builder(packagePath, className)
                .addType(type)
                .build()
                .writeTo(codeGenerator, Dependencies(true, sources = sourceFiles.toTypedArray()))
        }
    }
}
