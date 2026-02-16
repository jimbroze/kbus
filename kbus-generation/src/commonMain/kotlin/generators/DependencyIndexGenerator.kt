package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.jimbroze.kbus.annotations.DependencyIndex
import com.jimbroze.kbus.annotations.DependencyInfo
import com.jimbroze.kbus.annotations.DependencyType
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependency
import com.jimbroze.kbus.generation.processing.dependencies.Dependency
import com.jimbroze.kbus.generation.processing.dependencies.DependencyWithChildren
import com.jimbroze.kbus.generation.processing.dependencies.FunctionalDependency
import com.jimbroze.kbus.generation.processing.dependencies.NonDependency
import com.jimbroze.kbus.generation.processing.dependencies.PropertyDependency
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.writeTo

class DependencyIndexGenerator(
    private val codeGenerator: CodeGenerator,
    @Suppress("unused") private val logger: KSPLogger,
    private val indexClassName: String,
) {
    fun generateIndexClass(packagePath: String, dependencies: Set<DependencyWithChildren>) {
        val classBuilder = TypeSpec.classBuilder(indexClassName)

        val infoSpecsBlock =
            dependencies
                .map { dependency -> CodeBlock.of("%L", this.addDependency(dependency)) }
                .joinToCode(", ")

        val indexAnnotation =
            AnnotationSpec.builder(DependencyIndex::class)
                .addMember("dependencies = [%L]", infoSpecsBlock)
                .build()

        classBuilder.addAnnotation(indexAnnotation)

        val file = FileSpec.builder(packagePath, indexClassName)
        file.addType(classBuilder.build())

        file.build().writeTo(codeGenerator, Dependencies(true))
    }

    private fun addDependency(dependency: DependencyWithChildren): AnnotationSpec {
        return AnnotationSpec.builder(DependencyInfo::class)
            .addMember(
                "${DependencyInfo::dependencyType.name} = %M",
                MemberName(
                    DependencyType::class.asClassName(),
                    dependencyAnnotationClass(dependency.metadata).toString(),
                ),
            )
            .addMember("${DependencyInfo::signature.name} = %S", dependency.metadata.signature)
            .addMember("${DependencyInfo::name.name} = %S", dependency.metadata.name)
            .addMember(
                "${DependencyInfo::cannotBeAutoloaded.name} = %L",
                dependency.cannotBeAutoloaded,
            )
            .addMember(
                "${DependencyInfo::requiresCommandDependencies.name} = %L",
                dependency.metadata.requiresCommandDependencies,
            )
            .addMember(
                "${DependencyInfo::topLevelDependencies.name} = [%L]",
                topLevelDependencies(dependency.topLevelDependencies),
            )
            .build()
    }

    private fun topLevelDependencies(dependencies: List<Dependency>): CodeBlock {
        return dependencies
            .map { it.typeName.toString() }
            .toTypedArray()
            .map { CodeBlock.of("%S", it) }
            .joinToCode(", ")
    }

    private fun dependencyAnnotationClass(dependency: Dependency): DependencyType {
        return when (dependency) {
            is FunctionalDependency -> DependencyType.FUNCTIONAL
            is PropertyDependency -> DependencyType.PROPERTY
            is CommandDependency -> DependencyType.COMMAND
            is NonDependency -> DependencyType.NON_DEPENDENCY
        }
    }
}
