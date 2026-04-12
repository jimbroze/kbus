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
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.reflect.KClass

class LoadedEventHandlersGenerator(
    private val codeGenerator: CodeGenerator,
    @Suppress("unused") private val logger: KSPLogger,
    val domainFileName: String,
    val integrationFileName: String,
    private val packagePath: String,
) {
    fun generateExtensionProperties(handlers: Set<HandlerDefinition>, sourceFiles: List<KSFile>) {
        val eventHandlers = handlers.filterIsInstance<EventHandlerDefinition>()
        if (eventHandlers.isEmpty()) return

        val domainHandlers = eventHandlers.filter { it.kind == EventHandlerKind.DOMAIN }
        val integrationHandlers = eventHandlers.filter { it.kind == EventHandlerKind.INTEGRATION }

        if (domainHandlers.isNotEmpty()) {
            generateFile(domainFileName, domainHandlers, sourceFiles)
        }

        if (integrationHandlers.isNotEmpty()) {
            generateFile(integrationFileName, integrationHandlers, sourceFiles)
        }
    }

    private fun generateFile(
        fileName: String,
        eventHandlers: List<EventHandlerDefinition>,
        sourceFiles: List<KSFile>,
    ) {
        val fileBuilder =
            FileSpec.builder(packagePath, fileName)
                .addAnnotation(
                    AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
                        .addMember(
                            "%T::class",
                            ClassName("com.jimbroze.kbus.core.registry", "GeneratedKBusApi"),
                        )
                        .build()
                )

        for (handler in eventHandlers) {
            fileBuilder.addProperty(buildExtensionProperty(handler))
        }

        fileBuilder
            .build()
            .writeTo(codeGenerator, Dependencies(true, sources = sourceFiles.toTypedArray()))
    }

    private fun buildExtensionProperty(handler: EventHandlerDefinition): PropertySpec {
        val handlerClass = handler.handlerData.handlerClass
        val messageClass = handler.handlerData.messageClass
        val returnType = LoadedEventHandler::class.asClassName().parameterizedBy(messageClass)
        val receiverType = KClass::class.asClassName().parameterizedBy(handlerClass)

        return PropertySpec.builder("loaded", returnType)
            .receiver(receiverType)
            .addModifiers(KModifier.PUBLIC)
            .getter(
                FunSpec.getterBuilder()
                    .addAnnotation(
                        AnnotationSpec.builder(ClassName("kotlin.jvm", "JvmName"))
                            .addMember("%S", "get_loaded_${handlerClass.simpleName}")
                            .build()
                    )
                    .addAnnotation(
                        AnnotationSpec.builder(ClassName("kotlin.js", "JsName"))
                            .addMember("%S", "get_loaded_${handlerClass.simpleName}")
                            .build()
                    )
                    .addStatement("return %T(this)", LoadedEventHandler::class.asClassName())
                    .build()
            )
            .build()
    }
}
