package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.core.module.BoundedContext
import com.jimbroze.kbus.core.module.BoundedContextId
import com.jimbroze.kbus.core.module.ContextBuilder
import com.jimbroze.kbus.core.module.ContextConfig
import com.jimbroze.kbus.core.module.OwningContext
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
    val outboxConfigClass: KClass<*>,
    val inboxTuningClass: KClass<*>,
)

private const val CONTEXTS_CLASS = "Contexts"

private val COROUTINE_SCOPE = ClassName("kotlinx.coroutines", "CoroutineScope")
private val DISPATCHERS = ClassName("kotlinx.coroutines", "Dispatchers")

/** The bus property holding [context]'s own handler factory. */
private fun factoryPropertyName(context: String, factoryBaseName: String): String =
    contextAccessorName(context) + factoryBaseName

/** The [BoundedContextId] a handler's raw (possibly blank/unassigned) declared module maps to. */
private fun contextIdKeyBlock(module: String): CodeBlock =
    if (module.isBlank()) CodeBlock.of("%T.DEFAULT", BoundedContextId::class)
    else CodeBlock.of("%T(%S)", BoundedContextId::class, module)

/** The [CONTEXTS_CLASS] property holding the context a handler's declared module runs in. */
private fun contextPropertyName(module: String): String =
    contextAccessorName(module.ifBlank { DEFAULT_CONTEXT })

/** The [CONTEXTS_CLASS] property holding [context]'s handler locator. */
private fun locatorName(context: String): String = "${contextAccessorName(context)}Locator"

/** The [CONTEXTS_CLASS] constructor parameter carrying [context]'s configuration. */
private fun configName(context: String): String = "${contextAccessorName(context)}Config"

/**
 * A context exists only as the result of registering it, so a context [CONTEXTS_CLASS] declares is
 * always one the bus runs.
 */
private fun buildContextProperty(context: String): PropertySpec =
    PropertySpec.builder(contextAccessorName(context), OwningContext::class)
        .initializer(
            "builder.register(%T(%L, %L, %L.inbox, %L.subscriptions))",
            BoundedContext::class,
            contextIdKeyBlock(contextIdentity(context)),
            locatorName(context),
            configName(context),
            configName(context),
        )
        .build()

class BusGenerator(
    private val codeGenerator: CodeGenerator,
    @Suppress("unused") private val logger: KSPLogger,
    private val config: BusConfig,
    private val packagePath: String,
) {
    fun generateClass(handlers: Set<HandlerDefinition>, sourceFiles: List<KSFile>) {
        val dependenciesClassName = ClassName(packagePath, config.dependenciesInterfaceName)
        val contexts = contextIdentities(handlers)
        val factoryClassNames = contexts.associateWith { factoryClassNameFor(it) }

        val contextsClassName = ClassName(packagePath, config.busClassName, CONTEXTS_CLASS)

        val classBuilder =
            TypeSpec.classBuilder(config.busClassName)
                .superclass(config.busSuperClass.asClassName().parameterizedBy(contextsClassName))
                .addSuperclassConstructorParameter(
                    "buildContexts = %L",
                    buildContextsLambda(contextsClassName, factoryClassNames.keys),
                )
                .addSuperclassConstructorParameter("transactionManager = transactionManager")
                .addSuperclassConstructorParameter("middlewares = middleware")
                .addSuperclassConstructorParameter("appScope = appScope")
                .addSuperclassConstructorParameter("outbox = outbox")
                .addSuperclassConstructorParameter("inboxTuning = inboxTuning")

        classBuilder.addType(buildContextsClass(factoryClassNames))
        BusConstructorGenerator(config)
            .build(classBuilder, dependenciesClassName, factoryClassNames)

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
     * The lambda the base bus calls to build this bus's contexts. It captures only constructor
     * parameters, so no context can be registered against a half-built bus.
     */
    private fun buildContextsLambda(
        contextsClassName: ClassName,
        contexts: Collection<String>,
    ): CodeBlock =
        CodeBlock.of(
            "{ builder -> %T(%L) }",
            contextsClassName,
            (listOf("builder") +
                    contexts.map { factoryName(it) } +
                    contexts.map { contextAccessorName(it) })
                .joinToString(", "),
        )

    /**
     * One configuration point per bounded context, covering both its integration and its domain
     * handlers. The declared [BoundedContext]s are never exposed, so nothing can subscribe to a
     * context once the bus holding it exists, and each locates handlers through its own factory.
     */
    private fun buildContextsClass(factoryClassNames: Map<String, ClassName>): TypeSpec {
        val constructorBuilder =
            FunSpec.constructorBuilder().addModifiers(KModifier.INTERNAL).apply {
                addParameter("builder", ContextBuilder::class)
                factoryClassNames.forEach { (context, className) ->
                    addParameter(factoryName(context), className)
                }
                factoryClassNames.keys.forEach { context ->
                    addParameter(configName(context), ContextConfig::class)
                }
            }
        val builder =
            TypeSpec.classBuilder(CONTEXTS_CLASS).primaryConstructor(constructorBuilder.build())

        factoryClassNames.keys.forEach { context ->
            builder.addProperty(
                PropertySpec.builder(
                        locatorName(context),
                        GenerationHandlerLocator::class.asClassName(),
                    )
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("%T(%L)", GenerationHandlerLocator::class, factoryName(context))
                    .build()
            )
            builder.addProperty(buildContextProperty(context))
        }
        return builder.build()
    }

    private fun factoryName(context: String): String =
        factoryPropertyName(context, config.handlerFactoryName)

    private fun factoryClassNameFor(context: String): ClassName =
        ClassName(packagePath, contextClassPrefix(context) + config.handlerFactoryName)

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

        // A command runs against its own owning context: its domain events dispatch there, and
        // any command it nests resolves there. Which context that is comes from the handler's own
        // declared module, known at generation time.
        val processorArgs =
            if (handler is CommandHandlerDefinition)
                CodeBlock.of(
                    "%L, boundedContexts.%L, handlerCreator",
                    messageType,
                    contextPropertyName(handler.handlerData.module),
                )
            else CodeBlock.of("%L, handlerCreator", messageType)

        val functionBuilder =
            FunSpec.builder(processMethod)
                .addModifiers(KModifier.SUSPEND)
                .addParameter(messageType, messageClass)
                .returns(returnTypeName)

        functionBuilder.addCode(
            CodeBlock.builder()
                .addStatement("checkStarted()")
                .beginControlFlow("val handlerCreator = { %L ->", factoryParametersWithTypes)
                .addStatement(
                    "%L.%L($factoryParameters)",
                    factoryName(contextOf(handler)),
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
private class BusConstructorGenerator(private val config: BusConfig) {
    /**
     * A context is described entirely by the value passed under its own name, so what a context
     * subscribes to is fixed before the bus exists and nothing can add to it afterwards.
     */
    private fun contextConfigParameters(contexts: Collection<String>): List<ParameterSpec> =
        contexts.map {
            ParameterSpec.builder(contextAccessorName(it), ContextConfig::class)
                .defaultValue("%T()", ContextConfig::class)
                .build()
        }

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

    private fun inboxTuningParameter(): ParameterSpec =
        ParameterSpec.builder(
                "inboxTuning",
                config.inboxTuningClass.asClassName().copy(nullable = true),
            )
            .defaultValue("null")
            .build()

    fun build(
        classBuilder: TypeSpec.Builder,
        dependenciesClassName: ClassName,
        factoryClassNames: Map<String, ClassName>,
    ) {
        val primaryConstructor =
            FunSpec.constructorBuilder().addModifiers(KModifier.PRIVATE).apply {
                factoryClassNames.forEach { (context, className) ->
                    addParameter(factoryName(context), className)
                }
            }

        classBuilder
            .primaryConstructor(
                primaryConstructor
                    .addParameter("transactionManager", config.transactionManagerClass)
                    .addParameter(middlewareListParameter())
                    .addParameter(appScopeParameter())
                    .addParameter(outboxParameter())
                    .addParameter(inboxTuningParameter())
                    .apply {
                        contextConfigParameters(factoryClassNames.keys).forEach(::addParameter)
                    }
                    .build()
            )
            .apply {
                factoryClassNames.forEach { (context, className) ->
                    addProperty(
                        PropertySpec.builder(factoryName(context), className)
                            .initializer(factoryName(context))
                            .addModifiers(KModifier.PRIVATE)
                            .build()
                    )
                }
            }
            .addFunction(buildLoaderConstructor(dependenciesClassName, factoryClassNames))
    }

    private fun factoryName(context: String): String =
        factoryPropertyName(context, config.handlerFactoryName)

    private fun buildLoaderConstructor(
        dependenciesClassName: ClassName,
        factoryClassNames: Map<String, ClassName>,
    ): FunSpec =
        FunSpec.constructorBuilder()
            .addParameter("loader", dependenciesClassName)
            .addParameter("transactionManager", config.transactionManagerClass)
            .addParameter(middlewareListParameter())
            .addParameter(appScopeParameter())
            .addParameter(outboxParameter())
            .addParameter(inboxTuningParameter())
            .apply { contextConfigParameters(factoryClassNames.keys).forEach(::addParameter) }
            .callThisConstructor(
                factoryClassNames.values.map { CodeBlock.of("%T(loader)", it) } +
                    listOf(
                        CodeBlock.of("transactionManager"),
                        CodeBlock.of("middleware"),
                        CodeBlock.of("appScope"),
                        CodeBlock.of("outbox"),
                        CodeBlock.of("inboxTuning"),
                    ) +
                    factoryClassNames.keys.map { CodeBlock.of(contextAccessorName(it)) }
            )
            .build()
}
