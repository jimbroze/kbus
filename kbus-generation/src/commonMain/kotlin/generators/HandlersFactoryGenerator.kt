package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.jimbroze.kbus.generation.HandlerDefinition
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
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

        val classBuilder = TypeSpec.classBuilder(factoryClassName).addSuperinterface(superClassName)

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

        handlers.forEach { addHandlerDefinition(classBuilder, it) }

        val file = FileSpec.builder(packagePath, factoryClassName)
        file.addType(classBuilder.build())
        file.build().writeTo(codeGenerator, Dependencies(true))
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
