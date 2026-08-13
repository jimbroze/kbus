package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.jimbroze.kbus.application.messages.command.ContextCommands
import com.jimbroze.kbus.application.messages.command.NestedCommandExecutor
import com.jimbroze.kbus.generation.processing.handlers.CommandHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo

private const val EXECUTOR_CONSTRUCTOR_PARAMETER = "commandExecutor"

/**
 * Generates each bounded context's typed view of nested command execution: an interface per module
 * naming the commands that module can see, and — in the generation root — the one implementation
 * satisfying every such interface for that context.
 */
class ContextCommandsGenerator(
    private val codeGenerator: CodeGenerator,
    @Suppress("unused") private val logger: KSPLogger,
    private val commandsInterfaceName: String,
    private val executorClassName: String,
    private val packagePath: String,
) {
    /** Returns each generated interface against the identity of the context it covers. */
    fun generateInterfaces(
        handlers: Set<HandlerDefinition>,
        sourceFiles: List<KSFile>,
    ): Map<String, ClassName> =
        commandsByContext(handlers).entries.associate { (context, commands) ->
            val interfaceName = contextClassPrefix(context) + commandsInterfaceName
            val builder =
                TypeSpec.interfaceBuilder(interfaceName).addSuperinterface(ContextCommands::class)

            commands.forEach { command ->
                builder.addFunction(
                    commandFunction(command).addModifiers(KModifier.ABSTRACT).build()
                )
            }

            write(interfaceName, builder.build(), sourceFiles)

            contextIdentity(context) to ClassName(packagePath, interfaceName)
        }

    /**
     * [interfacesByContext] are the per-module interfaces this run can see beyond the ones it
     * generates itself; one object implements them all so that handlers from every module of a
     * context can be given the same executor.
     */
    fun generateExecutors(
        handlers: Set<HandlerDefinition>,
        interfacesByContext: Map<String, List<ClassName>>,
        sourceFiles: List<KSFile>,
    ) {
        commandsByContext(handlers).forEach { (context, commands) ->
            val prefix = contextClassPrefix(context)
            val className = prefix + executorClassName
            val builder =
                TypeSpec.classBuilder(className)
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter(
                                EXECUTOR_CONSTRUCTOR_PARAMETER,
                                NestedCommandExecutor::class,
                            )
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder(
                                EXECUTOR_CONSTRUCTOR_PARAMETER,
                                NestedCommandExecutor::class,
                                KModifier.PRIVATE,
                            )
                            .initializer(EXECUTOR_CONSTRUCTOR_PARAMETER)
                            .build()
                    )
                    .addSuperinterface(NestedCommandExecutor::class, EXECUTOR_CONSTRUCTOR_PARAMETER)
                    .addSuperinterface(ClassName(packagePath, prefix + commandsInterfaceName))

            interfacesByContext[contextIdentity(context)].orEmpty().forEach {
                builder.addSuperinterface(it)
            }

            commands.forEach { command ->
                builder.addFunction(
                    commandFunction(command)
                        .addModifiers(KModifier.OVERRIDE)
                        .addStatement("return %L.execute(command)", EXECUTOR_CONSTRUCTOR_PARAMETER)
                        .build()
                )
            }

            write(className, builder.build(), sourceFiles)
        }
    }

    private fun commandsByContext(
        handlers: Set<HandlerDefinition>
    ): Map<String, List<CommandHandlerDefinition>> =
        handlers.filterIsInstance<CommandHandlerDefinition>().groupBy { contextOf(it) }

    private fun commandFunction(command: CommandHandlerDefinition): FunSpec.Builder =
        FunSpec.builder(commandFunctionName(command))
            .addModifiers(KModifier.SUSPEND)
            .addParameter("command", command.handlerData.messageClass)
            .returns(command.handlerData.returnType)

    private fun commandFunctionName(command: CommandHandlerDefinition): String =
        command.handlerData.messageClass.simpleName.replaceFirstChar { it.lowercase() }

    private fun write(fileName: String, type: TypeSpec, sourceFiles: List<KSFile>) {
        FileSpec.builder(packagePath, fileName)
            .addType(type)
            .build()
            .writeTo(codeGenerator, Dependencies(true, sources = sourceFiles.toTypedArray()))
    }
}
