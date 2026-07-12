package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.writeTo

class SerializerModuleGenerator(
    private val codeGenerator: CodeGenerator,
    @Suppress("unused") private val logger: KSPLogger,
    private val fileName: String,
    private val packagePath: String,
) {
    private val serializersModuleClass =
        ClassName("kotlinx.serialization.modules", "SerializersModule")
    private val kSerializerClass = ClassName("kotlinx.serialization", "KSerializer")
    private val messageBaseClass = Message::class.asClassName()

    fun generateSerializerProperties(handlers: Set<HandlerDefinition>, sourceFiles: List<KSFile>) {
        val serializableMessages =
            handlers
                .filter { it.handlerData.isSerializable }
                .map { it.handlerData.messageClass }
                .distinct()

        if (serializableMessages.isEmpty()) return

        FileSpec.builder(packagePath, fileName)
            .addProperty(buildSerializersModuleProperty(serializableMessages))
            .addProperty(buildSerializerMapProperty(serializableMessages))
            .build()
            .writeTo(codeGenerator, Dependencies(true, sources = sourceFiles.toTypedArray()))
    }

    private fun buildSerializersModuleProperty(messageClasses: List<ClassName>): PropertySpec {
        val initializerBlock =
            CodeBlock.builder()
                .beginControlFlow("%T", serializersModuleClass)
                .apply {
                    messageClasses.forEach { msgClass ->
                        addStatement(
                            "polymorphic(%T::class, %T::class, %T.serializer())",
                            messageBaseClass,
                            msgClass,
                            msgClass,
                        )
                    }
                }
                .endControlFlow()
                .build()

        return PropertySpec.builder("KbusSerializersModule", serializersModuleClass)
            .initializer(initializerBlock)
            .build()
    }

    private fun buildSerializerMapProperty(messageClasses: List<ClassName>): PropertySpec {
        val mapType =
            Map::class.asClassName()
                .parameterizedBy(
                    String::class.asClassName(),
                    kSerializerClass.parameterizedBy(WildcardTypeName.producerOf(messageBaseClass)),
                )

        val mapBlock =
            CodeBlock.builder()
                .add("mapOf(\n")
                .indent()
                .apply {
                    messageClasses.forEach { msgClass ->
                        addStatement("%S to %T.serializer(),", msgClass.canonicalName, msgClass)
                    }
                }
                .unindent()
                .add(")")
                .build()

        return PropertySpec.builder("KbusSerializerMap", mapType).initializer(mapBlock).build()
    }
}
