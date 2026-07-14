package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.jimbroze.kbus.core.middleware.middleware.AutoPublishRegistration
import com.jimbroze.kbus.generation.processing.autopublish.AutoPublishDefinition
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.writeTo

class AutoPublishRegistrationsGenerator(
    private val codeGenerator: CodeGenerator,
    @Suppress("unused") private val logger: KSPLogger,
    private val fileName: String,
    private val packagePath: String,
) {
    fun generateRegistrations(definitions: Set<AutoPublishDefinition>, sourceFiles: List<KSFile>) {
        if (definitions.isEmpty()) return

        val sortedDefinitions = definitions.sortedBy { it.integrationEventClass.canonicalName }

        val propertyType =
            List::class.asClassName()
                .parameterizedBy(AutoPublishRegistration::class.asClassName().parameterizedBy(STAR))

        val initializer =
            CodeBlock.of(
                "listOf(%L)",
                sortedDefinitions
                    .map { definition ->
                        CodeBlock.of(
                            "%T(%T::class, %T)",
                            AutoPublishRegistration::class.asClassName(),
                            definition.domainEventClass,
                            definition.integrationEventClass,
                        )
                    }
                    .joinToCode(", "),
            )

        val property =
            PropertySpec.builder(fileName.replaceFirstChar { it.lowercase() }, propertyType)
                .addModifiers(KModifier.PUBLIC)
                .initializer(initializer)
                .build()

        FileSpec.builder(packagePath, fileName)
            .addProperty(property)
            .build()
            .writeTo(codeGenerator, Dependencies(true, sources = sourceFiles.toTypedArray()))
    }
}
