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
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class AutoLoaderGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val loaderInterfaceName: String,
    private val loaderClassName: String,
) {
    fun generateAutoloader(packagePath: String, dependencies: Set<DependencyWithChildren>) {
        val superClassName = ClassName(packagePath, loaderInterfaceName)

        val classBuilder =
            TypeSpec.classBuilder(loaderClassName)
                .addModifiers(KModifier.ABSTRACT)
                .addSuperinterface(superClassName)

        for (dependency in dependencies) {
            when (val metadata = dependency.metadata) {
                is FunctionalDependency ->
                    this.addFunctionalDependency(
                        classBuilder,
                        metadata,
                        dependency.topLevelDependencies,
                        dependency.isRoot,
                    )
                is PropertyDependency -> continue
                is CommandDependency -> continue
                is NonDependency -> continue
            }
        }

        val file = FileSpec.builder(packagePath, loaderClassName)
        file.addType(classBuilder.build())
        file.build().writeTo(codeGenerator, Dependencies(true))
    }

    private fun addFunctionalDependency(
        classBuilder: TypeSpec.Builder,
        dependency: FunctionalDependency,
        topLevelDependencies: List<Dependency>,
        isRoot: Boolean,
    ) {
        if (isRoot) return

        val returnType = dependency.typeRef.toTypeName()

        val subDependencyArgs = topLevelDependencies.joinToString(", ") { it.accessReference }

        val functionBuilder =
            FunSpec.builder(dependency.name)
                .addModifiers(KModifier.OVERRIDE)
                .returns(returnType)
                .addStatement("return %T($subDependencyArgs)", returnType)

        for (constructorArg in dependency.functionParameters) {
            functionBuilder.addParameter(constructorArg.name, constructorArg.typeRef)
        }

        classBuilder.addFunction(functionBuilder.build())
    }
}
