package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependency
import com.jimbroze.kbus.generation.processing.dependencies.DependencyWithChildren
import com.jimbroze.kbus.generation.processing.dependencies.FunctionalDependency
import com.jimbroze.kbus.generation.processing.dependencies.NonDependency
import com.jimbroze.kbus.generation.processing.dependencies.PropertyDependency
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo

class ContainerInterfaceGenerator(
    private val codeGenerator: CodeGenerator,
    @Suppress("unused") private val logger: KSPLogger,
    private val loaderInterfaceName: String,
    private val packagePath: String,
) {
    fun generateInterface(dependencies: Set<DependencyWithChildren>, sourceFiles: List<KSFile>) {
        val interfaceBuilder = TypeSpec.interfaceBuilder(loaderInterfaceName)

        for (dependency in dependencies) {
            when (val metadata = dependency.metadata) {
                is FunctionalDependency -> this.addFunctionalDependency(interfaceBuilder, metadata)
                is PropertyDependency -> this.addPropertyDependency(interfaceBuilder, metadata)
                is CommandDependency -> Unit
                is NonDependency -> Unit
            }
        }

        val file = FileSpec.builder(packagePath, loaderInterfaceName)
        file.addAnnotation(
            AnnotationSpec.builder(ClassName("kotlin", "Suppress"))
                .addMember("%S", "OPT_IN_USAGE")
                .addMember("%S", "OPT_IN_USAGE_ERROR")
                .build()
        )
        file.addType(interfaceBuilder.build())

        file
            .build()
            .writeTo(codeGenerator, Dependencies(true, sources = sourceFiles.toTypedArray()))
    }

    private fun addFunctionalDependency(
        interfaceBuilder: TypeSpec.Builder,
        dependency: FunctionalDependency,
    ) {
        val functionBuilder =
            FunSpec.builder(dependency.name)
                .addModifiers(KModifier.ABSTRACT)
                .returns(dependency.typeName)

        for (constructorArg in dependency.functionParameters) {
            functionBuilder.addParameter(constructorArg.name, constructorArg.typeRef)
        }

        interfaceBuilder.addFunction(functionBuilder.build())
    }

    private fun addPropertyDependency(
        interfaceBuilder: TypeSpec.Builder,
        dependency: PropertyDependency,
    ) {
        interfaceBuilder.addProperty(
            PropertySpec.builder(dependency.name, dependency.typeName).build()
        )
    }
}
