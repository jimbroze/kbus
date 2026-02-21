package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.reflect.KClass

data class BusConfig(
    val busClassName: String,
    val combinedDependenciesInterfaceName: String,
    val handlerFactoryName: String,
    val busSuperClass: KClass<*>,
    val middlewareClass: KClass<*>,
    val transactionManagerClass: KClass<*>,
    val handlerLocatorInterface: KClass<*>,
)

class BusGenerator(
    private val codeGenerator: CodeGenerator,
    @Suppress("unused") private val logger: KSPLogger,
    private val config: BusConfig,
    private val packagePath: String,
) {
    fun generateClass(handlers: Set<HandlerDefinition>) {
        val dependenciesClassName = ClassName(packagePath, config.combinedDependenciesInterfaceName)
        val handlerFactoryClassName = ClassName(packagePath, config.handlerFactoryName)

        val classBuilder =
            TypeSpec.classBuilder(config.busClassName)
                .superclass(config.busSuperClass)
                .addSuperclassConstructorParameter(
                    "%T(handlerFactory)",
                    config.handlerLocatorInterface,
                )
                .addSuperclassConstructorParameter("transactionManager")
                .addSuperclassConstructorParameter("middleware")

        classBuilder
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addModifiers(KModifier.PRIVATE)
                    .addParameter("handlerFactory", handlerFactoryClassName)
                    .addParameter("transactionManager", config.transactionManagerClass)
                    .addParameter(
                        "middleware",
                        List::class.asClassName()
                            .parameterizedBy(config.middlewareClass.asClassName()),
                    )
                    .build()
            )
            .addProperty(
                PropertySpec.builder("handlerFactory", handlerFactoryClassName)
                    .initializer("handlerFactory")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addFunction(
                FunSpec.constructorBuilder()
                    .addParameter("loader", dependenciesClassName)
                    .addParameter("transactionManager", config.transactionManagerClass)
                    .addParameter(
                        "middleware",
                        List::class.asClassName()
                            .parameterizedBy(config.middlewareClass.asClassName()),
                    )
                    .callThisConstructor(
                        CodeBlock.of("%T(loader)", handlerFactoryClassName),
                        CodeBlock.of("transactionManager"),
                        CodeBlock.of("middleware"),
                    )
                    .build()
            )

        handlers.forEach { classBuilder.addFunction(buildHandlerFunction(it)) }

        val file = FileSpec.builder(packagePath, config.busClassName)
        file.addType(classBuilder.build())
        file.build().writeTo(codeGenerator, Dependencies(true))
    }

    private fun buildHandlerFunction(handler: HandlerDefinition): FunSpec {
        val returnTypeName = handler.handlerData.returnType

        val messageType = handler.messageBaseClass.simpleName.replaceFirstChar { it.lowercase() }
        val messageClass = handler.handlerData.messageClass
        val messageProcessor = handler.messageProcessorName
        val processMethod = handler.processorMethodName

        val factoryParameters = handler.functionParameters.joinToString(", ") { it.name }
        val factoryParametersWithTypes =
            handler.functionParameters
                .map { parameter -> CodeBlock.of("${parameter.name}: %T", parameter.typeRef) }
                .joinToCode(", ")

        val functionBuilder =
            FunSpec.builder(processMethod)
                .addModifiers(KModifier.SUSPEND)
                .addParameter(messageType, messageClass)
                .returns(returnTypeName)

        functionBuilder.addCode(
            CodeBlock.builder()
                .beginControlFlow("val handlerCreator = { %L ->", factoryParametersWithTypes)
                .addStatement(
                    "handlerFactory.%L($factoryParameters)",
                    handler.handlerData.nameAsDependency,
                )
                .endControlFlow()
                .addStatement(
                    "return $messageProcessor.$processMethod($messageType, handlerCreator)"
                )
                .build()
        )

        return functionBuilder.build()
    }
}
