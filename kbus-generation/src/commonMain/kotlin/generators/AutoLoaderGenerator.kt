package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependency
import com.jimbroze.kbus.generation.processing.dependencies.Dependency
import com.jimbroze.kbus.generation.processing.dependencies.DependencyWithChildren
import com.jimbroze.kbus.generation.processing.dependencies.FunctionalDependency
import com.jimbroze.kbus.generation.processing.dependencies.NonDependency
import com.jimbroze.kbus.generation.processing.dependencies.PropertyDependency
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.writeTo

class AutoLoaderGenerator(
    private val codeGenerator: CodeGenerator,
    @Suppress("unused") private val logger: KSPLogger,
    private val loaderInterfaceName: String,
    private val loaderClassName: String,
    private val packagePath: String,
) {
    fun generateAutoloader(dependencies: Set<DependencyWithChildren>) {
        val superClassName = ClassName(packagePath, loaderInterfaceName)

        val classBuilder =
            TypeSpec.classBuilder(loaderClassName)
                .addModifiers(KModifier.ABSTRACT)
                .addSuperinterface(superClassName)

        for (dependency in dependencies) {
            if (dependency.cannotBeAutoloaded) continue

            when (val metadata = dependency.metadata) {
                is FunctionalDependency ->
                    classBuilder.addFunction(
                        this.generateLoaderFunction(metadata, dependency.topLevelDependencies)
                    )
                is PropertyDependency ->
                    classBuilder.addProperty(
                        this.generateLoaderProperty(metadata, dependency.topLevelDependencies)
                    )
                is CommandDependency -> Unit
                is NonDependency -> Unit
            }
        }

        val file = FileSpec.builder(packagePath, loaderClassName)
        file.addType(classBuilder.build())
        file.build().writeTo(codeGenerator, Dependencies(true))
    }

    private fun generateLoaderFunction(
        dependency: FunctionalDependency,
        topLevelDependencies: List<Dependency>,
    ): FunSpec {
        val returnType = dependency.typeName

        val arguments =
            topLevelDependencies.joinToCode(", ") { CodeBlock.of("%L", it.accessReference) }

        val parameterSpecs =
            dependency.functionParameters.map { arg ->
                ParameterSpec.builder(arg.name, arg.typeRef).build()
            }

        val functionBuilder =
            FunSpec.builder(dependency.name)
                .addModifiers(KModifier.OVERRIDE)
                .returns(returnType)
                .addParameters(parameterSpecs)
                .addStatement("return %T(%L)", returnType, arguments)

        return functionBuilder.build()
    }

    private fun generateLoaderProperty(
        dependency: PropertyDependency,
        topLevelDependencies: List<Dependency>,
    ): PropertySpec {
        val propertyType = dependency.typeName

        val arguments =
            topLevelDependencies.joinToCode(", ") { CodeBlock.of("%L", it.accessReference) }

        val propertyBuilder =
            PropertySpec.builder(dependency.name, propertyType)
                .addModifiers(KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement("return %T(%L)", propertyType, arguments)
                        .build()
                )

        return propertyBuilder.build()
    }
}
