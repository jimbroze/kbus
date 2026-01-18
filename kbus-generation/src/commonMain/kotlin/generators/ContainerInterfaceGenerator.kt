package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependency
import com.jimbroze.kbus.generation.processing.dependencies.DependencyWithChildren
import com.jimbroze.kbus.generation.processing.dependencies.FunctionalDependency
import com.jimbroze.kbus.generation.processing.dependencies.NonDependency
import com.jimbroze.kbus.generation.processing.dependencies.PropertyDependency
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class ContainerInterfaceGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val loaderInterfaceName: String,
    private val combinedInterfaceName: String,
) {
    fun generateCombinedInterface(packagePath: String, parents: Set<KSClassDeclaration>) {
        val interfaceBuilder = TypeSpec.interfaceBuilder(combinedInterfaceName)

        for (parent in parents) {
            interfaceBuilder.addSuperinterface(parent.asStarProjectedType().toTypeName())
        }

        val file = FileSpec.builder(packagePath, combinedInterfaceName)
        file.addType(interfaceBuilder.build())

        file.build().writeTo(codeGenerator, Dependencies(true))
    }

    fun generateInterface(packagePath: String, dependencies: Set<DependencyWithChildren>) {
        val interfaceBuilder = TypeSpec.interfaceBuilder(loaderInterfaceName)

        for (dependency in dependencies) {
            when (val metadata = dependency.metadata) {
                is FunctionalDependency -> this.addFunctionalDependency(interfaceBuilder, metadata)
                is PropertyDependency -> this.addPropertyDependency(interfaceBuilder, metadata)
                is CommandDependency -> continue
                is NonDependency -> continue
            }
        }

        val file = FileSpec.builder(packagePath, loaderInterfaceName)
        file.addType(interfaceBuilder.build())

        file.build().writeTo(codeGenerator, Dependencies(true))
    }

    private fun addFunctionalDependency(
        interfaceBuilder: TypeSpec.Builder,
        dependency: FunctionalDependency,
    ) {
        val functionBuilder =
            FunSpec.builder(dependency.name)
                .addModifiers(KModifier.ABSTRACT)
                .returns(dependency.typeRef.toTypeName())

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
            PropertySpec.builder(dependency.name, dependency.typeRef.toTypeName()).build()
        )
    }
}
