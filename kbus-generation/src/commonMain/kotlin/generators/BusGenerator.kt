package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.core.module.BoundedContext
import com.jimbroze.kbus.core.module.BoundedContextId
import com.jimbroze.kbus.core.registry.CompileTimeDomainEventMapper
import com.jimbroze.kbus.core.registry.EventMapperProvider
import com.jimbroze.kbus.core.registry.generation.GenerationHandlerLocator
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerKind
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.reflect.KClass

data class BusConfig(
    val busClassName: String,
    val dependenciesInterfaceName: String,
    val handlerFactoryName: String,
    val busSuperClass: KClass<*>,
    val middlewareClass: KClass<*>,
    val transactionManagerClass: KClass<*>,
    val handlerLocatorInterface: KClass<*>,
    val outboxConfigClass: KClass<*>,
    val inboxConfigClass: KClass<*>,
)

private const val DEFAULT_CONTEXT = "default"

private val COROUTINE_SCOPE = ClassName("kotlinx.coroutines", "CoroutineScope")
private val DISPATCHERS = ClassName("kotlinx.coroutines", "Dispatchers")

class BusGenerator(
    private val codeGenerator: CodeGenerator,
    @Suppress("unused") private val logger: KSPLogger,
    private val config: BusConfig,
    private val packagePath: String,
) {
    fun generateClass(handlers: Set<HandlerDefinition>, sourceFiles: List<KSFile>) {
        val dependenciesClassName = ClassName(packagePath, config.dependenciesInterfaceName)
        val handlerFactoryClassName = ClassName(packagePath, config.handlerFactoryName)

        val classBuilder =
            TypeSpec.classBuilder(config.busClassName)
                .superclass(config.busSuperClass)
                .addSuperclassConstructorParameter(
                    "%T(handlerFactory)",
                    config.handlerLocatorInterface,
                )
                .addSuperclassConstructorParameter("transactionManager")
                .addSuperclassConstructorParameter("middleware")
                .addSuperclassConstructorParameter("appScope = appScope")
                .addSuperclassConstructorParameter("outbox = outbox")
                .addSuperclassConstructorParameter("contexts = contextLocators.values.toList()")
                .addSuperclassConstructorParameter("inbox = inbox")

        val contexts = contextIdentities(handlers)
        BusConstructorGenerator(config)
            .build(classBuilder, dependenciesClassName, handlerFactoryClassName, contexts)
        buildEventMapperProperties(classBuilder, contexts)

        handlers
            .filterNot { it is EventHandlerDefinition }
            .forEach { classBuilder.addFunction(buildHandlerFunction(it)) }

        classBuilder.addFunction(buildDeprecatedExecute())
        classBuilder.addFunction(buildDeprecatedFetch())

        val file = FileSpec.builder(packagePath, config.busClassName)
        file.addType(classBuilder.build())
        file
            .build()
            .writeTo(codeGenerator, Dependencies(true, sources = sourceFiles.toTypedArray()))
    }

    /**
     * The bounded contexts this bus wires up: every distinct identity stamped on an integration
     * event handler, plus the default context, which owns every handler whose producing module
     * declared none.
     */
    private fun contextIdentities(handlers: Set<HandlerDefinition>): List<String> {
        val modules =
            handlers
                .filterIsInstance<EventHandlerDefinition>()
                .filter { it.kind == EventHandlerKind.INTEGRATION }
                .map { it.handlerData.module }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()

        return listOf(DEFAULT_CONTEXT) + modules
    }

    private fun accessorName(context: String): String =
        context
            .split('-', '_', '.')
            .mapIndexed { index, segment ->
                if (index == 0) segment.replaceFirstChar { it.lowercase() }
                else segment.replaceFirstChar { it.uppercase() }
            }
            .joinToString("")

    private fun buildEventMapperProperties(classBuilder: TypeSpec.Builder, contexts: List<String>) {
        classBuilder.addProperty(
            PropertySpec.builder(
                    "domainEventMapper",
                    CompileTimeDomainEventMapper::class.asClassName(),
                )
                .initializer(
                    "%T((handlerLocator as %T).domainEventMapper)",
                    CompileTimeDomainEventMapper::class,
                    EventMapperProvider::class,
                )
                .build()
        )

        // One registration point per bounded context. There is deliberately no bus-wide
        // `integrationEventMapper`: with N contexts, "which context?" has no answer.
        contexts.forEach { context ->
            val key =
                if (context == DEFAULT_CONTEXT) CodeBlock.of("%T.DEFAULT", BoundedContextId::class)
                else CodeBlock.of("%T(%S)", BoundedContextId::class, context)

            classBuilder.addProperty(
                PropertySpec.builder(accessorName(context), BoundedContext::class.asClassName())
                    .initializer("contextLocators.getValue(%L)", key)
                    .build()
            )
        }
    }

    private fun buildHandlerFunction(handler: HandlerDefinition): FunSpec {
        val returnTypeName = handler.handlerData.returnType

        val messageType = handler.messageBaseClass.simpleName.replaceFirstChar { it.lowercase() }
        val messageClass = handler.handlerData.messageClass
        val messageProcessor = handler.messageProcessorName
        val processMethod = handler.processorMethodName

        val factoryParameters = handler.functionParameters.joinToString(", ") { it.name }
        val factoryParametersWithTypes =
            handler.functionParameters
                .map { parameter -> CodeBlock.of("${parameter.name}: %T", parameter.typeRef) }
                .joinToCode(", ")

        val functionBuilder =
            FunSpec.builder(processMethod)
                .addModifiers(KModifier.SUSPEND)
                .addParameter(messageType, messageClass)
                .returns(returnTypeName)

        functionBuilder.addCode(
            CodeBlock.builder()
                .beginControlFlow("val handlerCreator = { %L ->", factoryParametersWithTypes)
                .addStatement(
                    "handlerFactory.%L($factoryParameters)",
                    handler.handlerData.nameAsDependency,
                )
                .endControlFlow()
                .addStatement(
                    "return $messageProcessor.$processMethod($messageType, handlerCreator)"
                )
                .build()
        )

        return functionBuilder.build()
    }

    private fun buildDeprecatedExecute(): FunSpec {
        val tResult =
            TypeVariableName(
                "TResult",
                ClassName("com.jimbroze.kbus.contracts.result", "KBusResult"),
            )
        val tCommand =
            TypeVariableName("TCommand", Command::class.asClassName().parameterizedBy(tResult))

        return FunSpec.builder("execute")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addAnnotation(
                AnnotationSpec.builder(Deprecated::class)
                    .addMember(
                        "message = %S",
                        "This command has not been loaded. Are you missing a @LoadMessageHandler annotation?",
                    )
                    .addMember("level = %T.%L", DeprecationLevel::class, "ERROR")
                    .build()
            )
            .addTypeVariable(tCommand)
            .addTypeVariable(tResult)
            .addParameter("command", tCommand)
            .returns(tResult)
            .addStatement("error(%S)", "Should not be called directly on the compile-time bus.")
            .build()
    }

    private fun buildDeprecatedFetch(): FunSpec {
        val tResult =
            TypeVariableName(
                "TResult",
                ClassName("com.jimbroze.kbus.contracts.result", "KBusResult"),
            )
        val tQuery = TypeVariableName("TQuery", Query::class.asClassName().parameterizedBy(tResult))

        return FunSpec.builder("fetch")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addAnnotation(
                AnnotationSpec.builder(Deprecated::class)
                    .addMember(
                        "message = %S",
                        "This query has not been loaded. Are you missing a @LoadMessageHandler annotation?",
                    )
                    .addMember("level = %T.%L", DeprecationLevel::class, "ERROR")
                    .build()
            )
            .addTypeVariable(tQuery)
            .addTypeVariable(tResult)
            .addParameter("query", tQuery)
            .returns(tResult)
            .addStatement("error(%S)", "Should not be called directly on the compile-time bus.")
            .build()
    }
}

/** Builds the generated bus's constructors and the bounded-context locator map they wire up. */
private class BusConstructorGenerator(private val config: BusConfig) {
    private fun contextLocatorsBlock(contexts: List<String>): CodeBlock =
        contexts
            .map { context ->
                val key =
                    if (context == DEFAULT_CONTEXT)
                        CodeBlock.of("%T.DEFAULT", BoundedContextId::class)
                    else CodeBlock.of("%T(%S)", BoundedContextId::class, context)

                CodeBlock.of(
                    "%L to %T(%L, %T(handlerFactory))",
                    key,
                    BoundedContext::class,
                    key,
                    GenerationHandlerLocator::class,
                )
            }
            .joinToCode(", ", "mapOf(", ")")

    private fun middlewareListParameter(): ParameterSpec =
        ParameterSpec.builder(
                "middleware",
                List::class.asClassName().parameterizedBy(config.middlewareClass.asClassName()),
            )
            .build()

    private fun appScopeParameter(): ParameterSpec =
        ParameterSpec.builder("appScope", COROUTINE_SCOPE)
            .defaultValue("%T(%T.Default)", COROUTINE_SCOPE, DISPATCHERS)
            .build()

    private fun outboxParameter(): ParameterSpec =
        ParameterSpec.builder(
                "outbox",
                config.outboxConfigClass.asClassName().copy(nullable = true),
            )
            .defaultValue("null")
            .build()

    private fun inboxParameter(): ParameterSpec =
        ParameterSpec.builder("inbox", config.inboxConfigClass.asClassName().copy(nullable = true))
            .defaultValue("null")
            .build()

    private fun contextLocatorsType() =
        Map::class.asClassName()
            .parameterizedBy(
                BoundedContextId::class.asClassName(),
                BoundedContext::class.asClassName(),
            )

    fun build(
        classBuilder: TypeSpec.Builder,
        dependenciesClassName: ClassName,
        handlerFactoryClassName: ClassName,
        contexts: List<String>,
    ) {
        classBuilder
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addModifiers(KModifier.PRIVATE)
                    .addParameter("handlerFactory", handlerFactoryClassName)
                    .addParameter("contextLocators", contextLocatorsType())
                    .addParameter("transactionManager", config.transactionManagerClass)
                    .addParameter(middlewareListParameter())
                    .addParameter(appScopeParameter())
                    .addParameter(outboxParameter())
                    .addParameter(inboxParameter())
                    .build()
            )
            .addProperty(
                PropertySpec.builder("handlerFactory", handlerFactoryClassName)
                    .initializer("handlerFactory")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("contextLocators", contextLocatorsType())
                    .initializer("contextLocators")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addFunction(buildContextBuildingConstructor(handlerFactoryClassName, contexts))
            .addFunction(buildLoaderConstructor(dependenciesClassName, handlerFactoryClassName))
    }

    /** Builds this bus's bounded contexts, all sharing the one generated handler factory. */
    private fun buildContextBuildingConstructor(
        handlerFactoryClassName: ClassName,
        contexts: List<String>,
    ): FunSpec =
        FunSpec.constructorBuilder()
            .addModifiers(KModifier.PRIVATE)
            .addParameter("handlerFactory", handlerFactoryClassName)
            .addParameter("transactionManager", config.transactionManagerClass)
            .addParameter(middlewareListParameter())
            .addParameter(appScopeParameter())
            .addParameter(outboxParameter())
            .addParameter(inboxParameter())
            .callThisConstructor(
                CodeBlock.of("handlerFactory"),
                contextLocatorsBlock(contexts),
                CodeBlock.of("transactionManager"),
                CodeBlock.of("middleware"),
                CodeBlock.of("appScope"),
                CodeBlock.of("outbox"),
                CodeBlock.of("inbox"),
            )
            .build()

    private fun buildLoaderConstructor(
        dependenciesClassName: ClassName,
        handlerFactoryClassName: ClassName,
    ): FunSpec =
        FunSpec.constructorBuilder()
            .addParameter("loader", dependenciesClassName)
            .addParameter("transactionManager", config.transactionManagerClass)
            .addParameter(middlewareListParameter())
            .addParameter(appScopeParameter())
            .addParameter(outboxParameter())
            .addParameter(inboxParameter())
            .callThisConstructor(
                CodeBlock.of("%T(loader)", handlerFactoryClassName),
                CodeBlock.of("transactionManager"),
                CodeBlock.of("middleware"),
                CodeBlock.of("appScope"),
                CodeBlock.of("outbox"),
                CodeBlock.of("inbox"),
            )
            .build()
}
