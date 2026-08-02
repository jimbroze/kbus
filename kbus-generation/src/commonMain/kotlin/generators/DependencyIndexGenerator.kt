package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.jimbroze.kbus.contracts.annotations.index.AutoPublishInfo
import com.jimbroze.kbus.contracts.annotations.index.ContextCommandsInfo
import com.jimbroze.kbus.contracts.annotations.index.DependencyInfo
import com.jimbroze.kbus.contracts.annotations.index.DependencyType
import com.jimbroze.kbus.contracts.annotations.index.HandlerInfo
import com.jimbroze.kbus.contracts.annotations.index.HandlerType
import com.jimbroze.kbus.contracts.annotations.index.KbusIndex
import com.jimbroze.kbus.contracts.annotations.index.RequiredDependencies
import com.jimbroze.kbus.generation.processing.autopublish.AutoPublishDefinition
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependency
import com.jimbroze.kbus.generation.processing.dependencies.ContextCommandsDependency
import com.jimbroze.kbus.generation.processing.dependencies.Dependency
import com.jimbroze.kbus.generation.processing.dependencies.DependencyWithChildren
import com.jimbroze.kbus.generation.processing.dependencies.FunctionalDependency
import com.jimbroze.kbus.generation.processing.dependencies.NonDependency
import com.jimbroze.kbus.generation.processing.dependencies.PropertyDependency
import com.jimbroze.kbus.generation.processing.handlers.CommandHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerKind
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.QueryHandlerDefinition
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
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
    private val packagePath: String,
) {
    fun generateIndexClass(
        dependencies: Set<DependencyWithChildren>,
        handlers: Set<HandlerDefinition>,
        autoPublishDefinitions: Set<AutoPublishDefinition>,
        contextCommandInterfaces: Map<String, ClassName>,
        sourceFiles: List<KSFile>,
    ) {
        val classBuilder = TypeSpec.classBuilder(indexClassName)

        val dependencyInfoSpecsBlock =
            dependencies
                .map { dependency -> CodeBlock.of("%L", this.addDependency(dependency)) }
                .joinToCode(", ")

        val handlerInfoSpecsBlock =
            handlers
                .map { handler -> CodeBlock.of("%L", this.addHandler(handler)) }
                .joinToCode(", ")

        val indexAnnotationBuilder =
            AnnotationSpec.builder(KbusIndex::class)
                .addMember("dependencies = [%L]", dependencyInfoSpecsBlock)
                .addMember("handlers = [%L]", handlerInfoSpecsBlock)

        if (autoPublishDefinitions.isNotEmpty()) {
            val autoPublishInfoSpecsBlock =
                autoPublishDefinitions
                    .map { definition -> CodeBlock.of("%L", this.addAutoPublish(definition)) }
                    .joinToCode(", ")
            indexAnnotationBuilder.addMember("autoPublishEvents = [%L]", autoPublishInfoSpecsBlock)
        }

        if (contextCommandInterfaces.isNotEmpty()) {
            val contextCommandsSpecsBlock =
                contextCommandInterfaces.entries
                    .map { (identity, interfaceClass) ->
                        CodeBlock.of("%L", this.addContextCommands(identity, interfaceClass))
                    }
                    .joinToCode(", ")
            indexAnnotationBuilder.addMember("contextCommands = [%L]", contextCommandsSpecsBlock)
        }

        classBuilder.addAnnotation(indexAnnotationBuilder.build())

        val file = FileSpec.builder(packagePath, indexClassName)
        file.addType(classBuilder.build())

        file
            .build()
            .writeTo(codeGenerator, Dependencies(true, sources = sourceFiles.toTypedArray()))
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
                "${DependencyInfo::requiredDependencies.name} = %M",
                MemberName(
                    RequiredDependencies::class.asClassName(),
                    dependency.metadata.requiredDependencies.name,
                ),
            )
            .addMember(
                "${DependencyInfo::topLevelDependencies.name} = [%L]",
                topLevelDependencies(dependency.topLevelDependencies),
            )
            .build()
    }

    private fun addHandler(handler: HandlerDefinition): AnnotationSpec {
        return AnnotationSpec.builder(HandlerInfo::class)
            .addMember(
                "${HandlerInfo::handlerType.name} = %M",
                MemberName(
                    HandlerType::class.asClassName(),
                    handlerAnnotationClass(handler).toString(),
                ),
            )
            .addMember(
                "${HandlerInfo::handlerClass.name} = %S",
                handler.handlerData.handlerClass.canonicalName,
            )
            .addMember(
                "${HandlerInfo::messageClass.name} = %S",
                handler.handlerData.messageClass.canonicalName,
            )
            .addMember(
                "${HandlerInfo::returnType.name} = %S",
                handler.handlerData.returnType.toString(),
            )
            .addMember(
                "${HandlerInfo::topLevelDependencies.name} = [%L]",
                topLevelDependencies(handler.handlerData.topLevelDependencies),
            )
            // Always emitted: IndexParser errors on a missing or null annotation argument.
            .addMember("${HandlerInfo::module.name} = %S", handler.handlerData.module)
            .build()
    }

    private fun addAutoPublish(definition: AutoPublishDefinition): AnnotationSpec {
        return AnnotationSpec.builder(AutoPublishInfo::class)
            .addMember(
                "${AutoPublishInfo::integrationEventClass.name} = %S",
                definition.integrationEventClass.canonicalName,
            )
            .addMember(
                "${AutoPublishInfo::domainEventClass.name} = %S",
                definition.domainEventClass.canonicalName,
            )
            .build()
    }

    private fun addContextCommands(identity: String, interfaceClass: ClassName): AnnotationSpec {
        return AnnotationSpec.builder(ContextCommandsInfo::class)
            .addMember("${ContextCommandsInfo::contextIdentity.name} = %S", identity)
            .addMember(
                "${ContextCommandsInfo::interfaceClass.name} = %S",
                interfaceClass.canonicalName,
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
            is ContextCommandsDependency -> DependencyType.CONTEXT_COMMANDS
            is NonDependency -> DependencyType.NON_DEPENDENCY
        }
    }

    private fun handlerAnnotationClass(handler: HandlerDefinition): HandlerType {
        return when (handler) {
            is CommandHandlerDefinition -> HandlerType.COMMAND
            is QueryHandlerDefinition -> HandlerType.QUERY
            is EventHandlerDefinition ->
                when (handler.kind) {
                    EventHandlerKind.DOMAIN -> HandlerType.DOMAIN_EVENT
                    EventHandlerKind.INTEGRATION -> HandlerType.INTEGRATION_EVENT
                }
        }
    }
}
