package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.jimbroze.kbus.generation.HandlerDefinition
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.reflect.KClass

class BusGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val busClassName: String,
    private val combinedDependenciesInterfaceName: String,
    private val handlerFactoryName: String,
    private val busSuperClass: KClass<*>,
    private val middlewareClass: KClass<*>,
    private val transactionManagerClass: KClass<*>,
    private val handlerLocatorInterface: KClass<*>,
) {
    fun generateClass(packagePath: String, handlers: Set<HandlerDefinition>) {
        val dependenciesClassName = ClassName(packagePath, combinedDependenciesInterfaceName)
        val handlerFactoryClassName = ClassName(packagePath, handlerFactoryName)

        val classBuilder =
            TypeSpec.classBuilder(busClassName)
                .superclass(busSuperClass)
                .addSuperclassConstructorParameter("%T(handlerFactory)", handlerLocatorInterface)
                .addSuperclassConstructorParameter("transactionManager")
                .addSuperclassConstructorParameter("middleware")

        classBuilder
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addModifiers(KModifier.PRIVATE)
                    .addParameter("handlerFactory", handlerFactoryClassName)
                    .addParameter("transactionManager", transactionManagerClass)
                    .addParameter(
                        "middleware",
                        List::class.asClassName().parameterizedBy(middlewareClass.asClassName()),
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
                    .addParameter("transactionManager", transactionManagerClass)
                    .addParameter(
                        "middleware",
                        List::class.asClassName().parameterizedBy(middlewareClass.asClassName()),
                    )
                    .callThisConstructor(
                        CodeBlock.of("%T(loader)", handlerFactoryClassName),
                        CodeBlock.of("transactionManager"),
                        CodeBlock.of("middleware"),
                    )
                    .build()
            )

        handlers.forEach { classBuilder.addFunction(buildHandlerFunction(it)) }

        val file = FileSpec.builder(packagePath, busClassName)
        file.addType(classBuilder.build())
        file.build().writeTo(codeGenerator, Dependencies(true))
    }

    private fun buildHandlerFunction(handler: HandlerDefinition): FunSpec {
        val returnTypeName = handler.handlerData.returnType.toTypeName()

        val messageType =
            handler.messageBaseClass.simpleName?.replaceFirstChar { it.lowercase() }
                ?: error(
                    "Message base class simple name is missing for: ${handler.messageBaseClass.qualifiedName}"
                )
        val messageClass = handler.handlerData.messageClass.asStarProjectedType().toTypeName()
        val messageProcessor = handler.messageProcessorName
        val processMethod = handler.processorMethodName

        val factoryParameters = handler.functionParameters.joinToString(", ") { it.name }
        val lambdaParamsFormat = handler.functionParameters.joinToString(", ") { "${it.name}: %T" }
        val lambdaParamTypes = handler.functionParameters.map { it.typeRef }.toTypedArray()

        val functionBuilder =
            FunSpec.builder(processMethod)
                .addModifiers(KModifier.SUSPEND)
                .addParameter(messageType, messageClass)
                .returns(returnTypeName)

        functionBuilder.addCode(
            CodeBlock.builder()
                .beginControlFlow(
                    "val handlerCreator = { $lambdaParamsFormat ->",
                    *lambdaParamTypes,
                )
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
