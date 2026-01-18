package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.jimbroze.kbus.core.Command
import com.jimbroze.kbus.core.CommandDependencies
import com.jimbroze.kbus.core.CommandHandler
import com.jimbroze.kbus.core.GenerationHandlerFactory
import com.jimbroze.kbus.core.Query
import com.jimbroze.kbus.core.QueryHandler
import com.jimbroze.kbus.generation.processing.handlers.CommandHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.QueryHandlerDefinition
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
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class HandlersFactoryGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val factoryClassName: String,
    private val combinedDependenciesInterfaceName: String,
    private val combinedHandlersInterfaceName: String,
) {
    fun generateClass(packagePath: String, handlers: Set<HandlerDefinition>) {
        val superClassName = ClassName(packagePath, combinedHandlersInterfaceName)
        val dependenciesClassName = ClassName(packagePath, combinedDependenciesInterfaceName)

        val classBuilder =
            TypeSpec.classBuilder(factoryClassName)
                .addSuperinterface(superClassName)
                .addSuperinterface(GenerationHandlerFactory::class)

        classBuilder
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("dependencies", dependenciesClassName)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("dependencies", dependenciesClassName, KModifier.PRIVATE)
                    .initializer("dependencies")
                    .build()
            )
            .addFunction(
                buildCommandsHandlersFor(
                    handlers.filterIsInstance<CommandHandlerDefinition>().toSet()
                )
            )
            .addFunction(
                buildQueriesHandlersFor(handlers.filterIsInstance<QueryHandlerDefinition>().toSet())
            )

        handlers.forEach { addHandlerDefinition(classBuilder, it) }

        val file = FileSpec.builder(packagePath, factoryClassName)
        file.addType(classBuilder.build())
        file.build().writeTo(codeGenerator, Dependencies(true))
    }

    private fun buildCommandsHandlersFor(handlers: Set<CommandHandlerDefinition>): FunSpec {
        val tResult = TypeVariableName("TResult", ClassName("com.jimbroze.kbus.core", "KBusResult"))
        val tCommand =
            TypeVariableName("TCommand", Command::class.asClassName().parameterizedBy(tResult))
        val returnType =
            CommandHandler::class.asClassName()
                .parameterizedBy(tCommand, tResult)
                .copy(nullable = true)

        val codeBlock =
            CodeBlock.builder()
                .addStatement("@Suppress(%S)", "UNCHECKED_CAST")
                .add("return when (command) {\n")
                .indent()

        for (handler in handlers) {
            val handlerName = handler.handlerData.nameAsDependency
            val commandClass = handler.handlerData.messageClass
            codeBlock.addStatement(
                "is %T -> this.$handlerName(commandDependencies)",
                commandClass.toClassName(),
            )
        }

        codeBlock.addStatement("else -> null").unindent().add("} as %T", returnType)

        val functionBuilder =
            FunSpec.builder("handlerFor")
                .addModifiers(KModifier.OVERRIDE)
                .addTypeVariables(listOf(tCommand, tResult))
                .returns(returnType)
                .addParameter("command", tCommand)
                .addParameter("commandDependencies", CommandDependencies::class)
                .addCode(codeBlock.build())

        return functionBuilder.build()
    }

    private fun buildQueriesHandlersFor(handlers: Set<QueryHandlerDefinition>): FunSpec {
        val tResult = TypeVariableName("TResult", ClassName("com.jimbroze.kbus.core", "KBusResult"))
        val tQuery = TypeVariableName("TQuery", Query::class.asClassName().parameterizedBy(tResult))
        val returnType =
            QueryHandler::class.asClassName().parameterizedBy(tQuery, tResult).copy(nullable = true)

        val codeBlock =
            CodeBlock.builder()
                .addStatement("@Suppress(%S)", "UNCHECKED_CAST")
                .add("return when (query) {\n")
                .indent()

        for (handler in handlers) {
            val handlerName = handler.handlerData.nameAsDependency
            val queryClass = handler.handlerData.messageClass
            codeBlock.addStatement("is %T -> this.$handlerName()", queryClass.toClassName())
        }

        codeBlock.addStatement("else -> null").unindent().add("} as %T", returnType)

        val functionBuilder =
            FunSpec.builder("handlerFor")
                .addModifiers(KModifier.OVERRIDE)
                .addTypeVariables(listOf(tQuery, tResult))
                .returns(returnType)
                .addParameter("query", tQuery)
                .addCode(codeBlock.build())

        return functionBuilder.build()
    }

    private fun addHandlerDefinition(classBuilder: TypeSpec.Builder, handler: HandlerDefinition) {
        val returnType = handler.handlerData.handlerClass.asStarProjectedType().toTypeName()

        val subDependencyArgs =
            handler.handlerData.topLevelDependencies.joinToString(", ") {
                "dependencies.${it.accessReference}"
            }

        val functionBuilder =
            FunSpec.builder(handler.handlerData.nameAsDependency)
                .addModifiers(KModifier.OVERRIDE)
                .returns(returnType)
                .addStatement("return %T($subDependencyArgs)", returnType)

        for (constructorParameter in handler.functionParameters) {
            functionBuilder.addParameter(constructorParameter.name, constructorParameter.typeRef)
        }

        classBuilder.addFunction(functionBuilder.build())
    }
}
