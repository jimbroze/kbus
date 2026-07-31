package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.core.module.BoundedContext
import com.jimbroze.kbus.core.module.BoundedContextId
import com.jimbroze.kbus.core.registry.generation.GenerationHandlerLocator
import com.jimbroze.kbus.generation.processing.handlers.CommandHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
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
private const val CONTEXTS_CLASS = "Contexts"

private val COROUTINE_SCOPE = ClassName("kotlinx.coroutines", "CoroutineScope")
private val DISPATCHERS = ClassName("kotlinx.coroutines", "Dispatchers")

/**
 * The bounded contexts a bus wires up: every distinct identity stamped on any handler — command,
 * query or event — plus the default context, which owns every handler whose producing module
 * declared none. A context defining only commands or only domain handlers still needs its own
 * entry, or its handlers would be unreachable by owner lookup.
 */
internal fun contextIdentities(handlers: Set<HandlerDefinition>): List<String> {
    val modules =
        handlers.map { it.handlerData.module }.filter { it.isNotBlank() }.distinct().sorted()

    return listOf(DEFAULT_CONTEXT) + modules
}

/** The [BoundedContextId] a handler's raw (possibly blank/unassigned) declared module maps to. */
private fun contextIdKeyBlock(module: String): CodeBlock =
    if (module.isBlank()) CodeBlock.of("%T.DEFAULT", BoundedContextId::class)
    else CodeBlock.of("%T(%S)", BoundedContextId::class, module)

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
                .addSuperclassConstructorParameter("contexts = contexts.all")
                .addSuperclassConstructorParameter("inbox = inbox")

        val contexts = contextIdentities(handlers)
        val contextsClassName = ClassName(packagePath, config.busClassName, CONTEXTS_CLASS)
        classBuilder.addType(buildContextsClass(handlerFactoryClassName, contexts))
        BusConstructorGenerator(config, contextsClassName)
            .build(classBuilder, dependenciesClassName, handlerFactoryClassName)
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

    private fun accessorName(context: String): String =
        context
            .split('-', '_', '.')
            .mapIndexed { index, segment ->
                if (index == 0) segment.replaceFirstChar { it.lowercase() }
                else segment.replaceFirstChar { it.uppercase() }
            }
            .joinToString("")

    /**
     * One registration point per bounded context, for both integration and domain handlers (via
     * [BoundedContext]'s own `addEventHandlers`/`addDomainHandlers`). There is deliberately no
     * bus-wide `integrationEventMapper` or `domainEventMapper`: with N contexts, "which context?"
     * has no answer for either — a command's domain events dispatch only to its owning context.
     */
    private fun buildEventMapperProperties(classBuilder: TypeSpec.Builder, contexts: List<String>) {
        contexts.forEach { context ->
            classBuilder.addProperty(
                PropertySpec.builder(accessorName(context), BoundedContext::class.asClassName())
                    .initializer("contexts.%L", accessorName(context))
                    .build()
            )
        }
    }

    /**
     * The bus's bounded contexts as standalone objects, constructed before the bus itself so that
     * event handlers can be registered against them while the bus is still being built. All share
     * the one generated handler factory, each disambiguated by its own context identity.
     */
    private fun buildContextsClass(
        handlerFactoryClassName: ClassName,
        contexts: List<String>,
    ): TypeSpec {
        val builder =
            TypeSpec.classBuilder(CONTEXTS_CLASS)
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addModifiers(KModifier.INTERNAL)
                        .addParameter("handlerFactory", handlerFactoryClassName)
                        .build()
                )

        contexts.forEach { context ->
            val identity = if (context == DEFAULT_CONTEXT) "" else context
            builder.addProperty(
                PropertySpec.builder(accessorName(context), BoundedContext::class.asClassName())
                    .initializer(
                        "%T(%L, %T(handlerFactory, %S))",
                        BoundedContext::class,
                        contextIdKeyBlock(identity),
                        GenerationHandlerLocator::class,
                        identity,
                    )
                    .build()
            )
        }

        return builder
            .addProperty(
                PropertySpec.builder(
                        "all",
                        List::class.asClassName()
                            .parameterizedBy(BoundedContext::class.asClassName()),
                    )
                    .addModifiers(KModifier.INTERNAL)
                    .initializer(
                        contexts
                            .map { CodeBlock.of("%L", accessorName(it)) }
                            .joinToCode(", ", "listOf(", ")")
                    )
                    .build()
            )
            .build()
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

        // A command's domain events must dispatch only to its own owning context, so
        // CommandExecutor.execute needs that context's dispatcher. The owning context is known at
        // generation time from the handler's own declared module, so it is baked in here rather
        // than resolved by searching every context for one that owns the command.
        val processorArgs =
            if (handler is CommandHandlerDefinition)
                CodeBlock.of(
                    "%L, domainEventDispatcherFor(%L), handlerCreator",
                    messageType,
                    contextIdKeyBlock(handler.handlerData.module),
                )
            else CodeBlock.of("%L, handlerCreator", messageType)

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
                .addStatement("return %L.%L(%L)", messageProcessor, processMethod, processorArgs)
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
private class BusConstructorGenerator(
    private val config: BusConfig,
    private val contextsClassName: ClassName,
) {
    /**
     * Registration against a context has to happen before the bus exists, so that a bus can close
     * registration as it is built. A delegating constructor's arguments are evaluated before the
     * constructor it delegates to, which is the only window where the contexts exist but the bus
     * does not.
     */
    private fun configureParameter(): ParameterSpec =
        ParameterSpec.builder(
                "configure",
                LambdaTypeName.get(receiver = contextsClassName, returnType = UNIT),
            )
            .defaultValue("{}")
            .build()

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

    fun build(
        classBuilder: TypeSpec.Builder,
        dependenciesClassName: ClassName,
        handlerFactoryClassName: ClassName,
    ) {
        classBuilder
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addModifiers(KModifier.PRIVATE)
                    .addParameter("handlerFactory", handlerFactoryClassName)
                    .addParameter("contexts", contextsClassName)
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
                PropertySpec.builder("contexts", contextsClassName)
                    .initializer("contexts")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addFunction(buildContextBuildingConstructor(handlerFactoryClassName))
            .addFunction(buildLoaderConstructor(dependenciesClassName, handlerFactoryClassName))
    }

    /**
     * Builds this bus's bounded contexts and applies `configure` to them, before the bus exists.
     */
    private fun buildContextBuildingConstructor(handlerFactoryClassName: ClassName): FunSpec =
        FunSpec.constructorBuilder()
            .addModifiers(KModifier.PRIVATE)
            .addParameter("handlerFactory", handlerFactoryClassName)
            .addParameter("transactionManager", config.transactionManagerClass)
            .addParameter(middlewareListParameter())
            .addParameter(appScopeParameter())
            .addParameter(outboxParameter())
            .addParameter(inboxParameter())
            .addParameter(configureParameter())
            .callThisConstructor(
                CodeBlock.of("handlerFactory"),
                CodeBlock.of("%T(handlerFactory).apply(configure)", contextsClassName),
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
            .addParameter(configureParameter())
            .callThisConstructor(
                CodeBlock.of("%T(loader)", handlerFactoryClassName),
                CodeBlock.of("transactionManager"),
                CodeBlock.of("middleware"),
                CodeBlock.of("appScope"),
                CodeBlock.of("outbox"),
                CodeBlock.of("inbox"),
                CodeBlock.of("configure"),
            )
            .build()
}
