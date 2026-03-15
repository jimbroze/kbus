package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.jimbroze.kbus.core.registry.LoadedEventHandler
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerKind
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.writeTo

class LoadedEventHandlersGenerator(
    private val codeGenerator: CodeGenerator,
    @Suppress("unused") private val logger: KSPLogger,
    val domainClassName: String,
    val integrationClassName: String,
    private val packagePath: String,
) {
    fun generateObjects(handlers: Set<HandlerDefinition>, sourceFiles: List<KSFile>) {
        val eventHandlers = handlers.filterIsInstance<EventHandlerDefinition>()
        if (eventHandlers.isEmpty()) return

        val domainHandlers = eventHandlers.filter { it.kind == EventHandlerKind.DOMAIN }
        val integrationHandlers = eventHandlers.filter { it.kind == EventHandlerKind.INTEGRATION }

        if (domainHandlers.isNotEmpty()) {
            generateObject(domainClassName, domainHandlers, sourceFiles)
        }

        if (integrationHandlers.isNotEmpty()) {
            generateObject(integrationClassName, integrationHandlers, sourceFiles)
        }
    }

    private fun generateObject(
        className: String,
        eventHandlers: List<EventHandlerDefinition>,
        sourceFiles: List<KSFile>,
    ) {
        val objectBuilder = TypeSpec.objectBuilder(className)

        for (handler in eventHandlers) {
            objectBuilder.addFunction(buildHandlerFunction(handler))
        }

        val file =
            FileSpec.builder(packagePath, className)
                .addAnnotation(
                    AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
                        .addMember(
                            "%T::class",
                            ClassName("com.jimbroze.kbus.core.registry", "GeneratedKBusApi"),
                        )
                        .build()
                )
                .addType(objectBuilder.build())

        file
            .build()
            .writeTo(codeGenerator, Dependencies(true, sources = sourceFiles.toTypedArray()))
    }

    private fun buildHandlerFunction(handler: EventHandlerDefinition): FunSpec {
        val handlerClass = handler.handlerData.handlerClass
        val messageClass = handler.handlerData.messageClass
        val returnType = LoadedEventHandler::class.asClassName().parameterizedBy(messageClass)

        return FunSpec.builder(handler.handlerData.nameAsDependency)
            .returns(returnType)
            .addStatement(
                "return %T(%T::class)",
                LoadedEventHandler::class.asClassName(),
                handlerClass,
            )
            .build()
    }
}
