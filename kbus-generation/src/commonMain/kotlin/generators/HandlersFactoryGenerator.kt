package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.registry.generation.GenerationHandlerFactory
import com.jimbroze.kbus.generation.processing.handlers.CommandHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.QueryHandlerDefinition
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
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.reflect.KClass

class HandlersFactoryGenerator(
    private val codeGenerator: CodeGenerator,
    @Suppress("unused") private val logger: KSPLogger,
    private val factoryClassName: String,
    private val dependenciesInterfaceName: String,
    private val handlersInterfaceName: String,
    private val packagePath: String,
) {
    fun generateClass(handlers: Set<HandlerDefinition>, sourceFiles: List<KSFile>) {
        val superClassName = ClassName(packagePath, handlersInterfaceName)
        val dependenciesClassName = ClassName(packagePath, dependenciesInterfaceName)

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
            .addFunction(
                buildEventHandlerFor(handlers.filterIsInstance<EventHandlerDefinition>().toSet())
            )

        handlers.forEach { addHandlerDefinition(classBuilder, it) }

        val file = FileSpec.builder(packagePath, factoryClassName)
        file.addAnnotation(
            AnnotationSpec.builder(ClassName("kotlin", "Suppress"))
                .addMember("%S", "OPT_IN_USAGE")
                .addMember("%S", "OPT_IN_USAGE_ERROR")
                .build()
        )
        file.addType(classBuilder.build())
        file
            .build()
            .writeTo(codeGenerator, Dependencies(true, sources = sourceFiles.toTypedArray()))
    }

    private fun buildCommandsHandlersFor(handlers: Set<CommandHandlerDefinition>): FunSpec {
        val tResult =
            TypeVariableName(
                "TResult",
                ClassName("com.jimbroze.kbus.contracts.result", "KBusResult"),
            )
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
            codeBlock.addStatement("is %T -> this.$handlerName(commandDependencies)", commandClass)
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

    // TODO make more polymorphic?
    private fun buildQueriesHandlersFor(handlers: Set<QueryHandlerDefinition>): FunSpec {
        val tResult =
            TypeVariableName(
                "TResult",
                ClassName("com.jimbroze.kbus.contracts.result", "KBusResult"),
            )
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
            codeBlock.addStatement("is %T -> this.$handlerName()", queryClass)
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

    private fun buildEventHandlerFor(handlers: Set<EventHandlerDefinition>): FunSpec {
        val tEvent = TypeVariableName("TEvent", Event::class.asClassName())

        val handlerClassType =
            KClass::class.asClassName()
                .parameterizedBy(EventHandler::class.asClassName().parameterizedBy(tEvent))
        val returnType =
            EventHandler::class.asClassName().parameterizedBy(tEvent).copy(nullable = true)

        val codeBlock =
            CodeBlock.builder()
                .addStatement("@Suppress(%S)", "UNCHECKED_CAST")
                .add("return when (handlerClass) {\n")
                .indent()

        for (handler in handlers) {
            val handlerName = handler.handlerData.nameAsDependency
            codeBlock.addStatement(
                "%T::class -> this.$handlerName()",
                handler.handlerData.handlerClass,
            )
        }

        codeBlock.addStatement("else -> null").unindent().add("} as %T", returnType)

        return FunSpec.builder("eventHandler")
            .addModifiers(KModifier.OVERRIDE)
            .addTypeVariables(listOf(tEvent))
            .addParameter("handlerClass", handlerClassType)
            .returns(returnType)
            .addCode(codeBlock.build())
            .build()
    }

    private fun addHandlerDefinition(classBuilder: TypeSpec.Builder, handler: HandlerDefinition) {
        val returnType = handler.handlerData.handlerClass

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
