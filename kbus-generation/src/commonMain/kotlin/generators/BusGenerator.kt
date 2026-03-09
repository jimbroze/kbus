package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.reflect.KClass

data class BusConfig(
    val busClassName: String,
    val dependenciesInterfaceName: String,
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
    fun generateClass(handlers: Set<HandlerDefinition>, sourceFiles: List<KSFile>) {
        val dependenciesClassName = ClassName(packagePath, config.dependenciesInterfaceName)
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

        classBuilder.addFunction(buildDeprecatedExecute())
        classBuilder.addFunction(buildDeprecatedFetch())

        val file = FileSpec.builder(packagePath, config.busClassName)
        file.addType(classBuilder.build())
        file
            .build()
            .writeTo(codeGenerator, Dependencies(true, sources = sourceFiles.toTypedArray()))
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

    private fun buildDeprecatedExecute(): FunSpec {
        val tResult =
            TypeVariableName(
                "TResult",
                ClassName("com.jimbroze.kbus.contracts.result", "KBusResult"),
            )
        val tCommand =
            TypeVariableName("TCommand", Command::class.asClassName().parameterizedBy(tResult))

        return FunSpec.builder("execute")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addAnnotation(
                AnnotationSpec.builder(Deprecated::class)
                    .addMember(
                        "message = %S",
                        "This command has not been loaded. Are you missing a @LoadMessageHandler annotation?",
                    )
                    .addMember("level = %T.%L", DeprecationLevel::class, "ERROR")
                    .build()
            )
            .addTypeVariable(tCommand)
            .addTypeVariable(tResult)
            .addParameter("command", tCommand)
            .returns(tResult)
            .addStatement("error(%S)", "Should not be called directly on the compile-time bus.")
            .build()
    }

    private fun buildDeprecatedFetch(): FunSpec {
        val tResult =
            TypeVariableName(
                "TResult",
                ClassName("com.jimbroze.kbus.contracts.result", "KBusResult"),
            )
        val tQuery = TypeVariableName("TQuery", Query::class.asClassName().parameterizedBy(tResult))

        return FunSpec.builder("fetch")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addAnnotation(
                AnnotationSpec.builder(Deprecated::class)
                    .addMember(
                        "message = %S",
                        "This query has not been loaded. Are you missing a @LoadMessageHandler annotation?",
                    )
                    .addMember("level = %T.%L", DeprecationLevel::class, "ERROR")
                    .build()
            )
            .addTypeVariable(tQuery)
            .addTypeVariable(tResult)
            .addParameter("query", tQuery)
            .returns(tResult)
            .addStatement("error(%S)", "Should not be called directly on the compile-time bus.")
            .build()
    }
}
