package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.core.module.BoundedContext
import com.jimbroze.kbus.core.module.BoundedContextId
import com.jimbroze.kbus.core.module.ContextRegistration
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
    val outboxConfigClass: KClass<*>,
    val inboxConfigClass: KClass<*>,
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

        val classBuilder =
            TypeSpec.classBuilder(config.busClassName)
                .superclass(config.busSuperClass)
                .addSuperclassConstructorParameter("transactionManager = transactionManager")
                .addSuperclassConstructorParameter("middlewares = middleware")
                .addSuperclassConstructorParameter("appScope = appScope")
                .addSuperclassConstructorParameter("outbox = outbox")
                .addSuperclassConstructorParameter("contexts = contexts.all")
                .addSuperclassConstructorParameter("inbox = inbox")

        val contextsClassName = ClassName(packagePath, config.busClassName, CONTEXTS_CLASS)
        classBuilder.addType(buildContextsClass(factoryClassNames))
        BusConstructorGenerator(config, contextsClassName)
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
     * One registration point per bounded context, for both integration and domain handlers. There
     * is deliberately no bus-wide `integrationEventMapper` or `domainEventMapper`: with N contexts,
     * "which context?" has no answer for either — a command's domain events dispatch only to its
     * owning context.
     *
     * Only the [ContextRegistration]s are exposed, never the [BoundedContext]s themselves: a bus
     * that handed those back would leave registration open for its whole lifetime. Each context
     * locates handlers through its own factory, so it can build no handler but its own.
     */
    private fun buildContextsClass(factoryClassNames: Map<String, ClassName>): TypeSpec {
        val constructorBuilder =
            FunSpec.constructorBuilder().addModifiers(KModifier.INTERNAL).apply {
                factoryClassNames.forEach { (context, className) ->
                    addParameter(factoryName(context), className)
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
            builder.addProperty(
                PropertySpec.builder(
                        contextAccessorName(context),
                        ContextRegistration::class.asClassName(),
                    )
                    .initializer("%T(%L)", ContextRegistration::class, locatorName(context))
                    .build()
            )
        }
        return builder
            .addProperty(buildContextListProperty(factoryClassNames.keys.toList()))
            .build()
    }

    /**
     * Built lazily because the registrations are configured after `Contexts` is constructed;
     * reading it eagerly would capture every context before its inbox had been declared.
     */
    private fun buildContextListProperty(contexts: List<String>): PropertySpec =
        PropertySpec.builder(
                "all",
                List::class.asClassName().parameterizedBy(BoundedContext::class.asClassName()),
            )
            .addModifiers(KModifier.INTERNAL)
            .delegate(
                CodeBlock.builder()
                    .beginControlFlow("lazy")
                    .add(
                        contexts
                            .map { context ->
                                CodeBlock.of(
                                    "%T(%L, %L, %L.inbox)",
                                    BoundedContext::class,
                                    contextIdKeyBlock(contextIdentity(context)),
                                    locatorName(context),
                                    contextAccessorName(context),
                                )
                            }
                            .joinToCode(", ", "listOf(", ")")
                    )
                    .add("\n")
                    .endControlFlow()
                    .build()
            )
            .build()

    private fun factoryName(context: String): String =
        factoryPropertyName(context, config.handlerFactoryName)

    private fun factoryClassNameFor(context: String): ClassName =
        ClassName(packagePath, contextClassPrefix(context) + config.handlerFactoryName)

    private fun locatorName(context: String): String = "${contextAccessorName(context)}Locator"

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

        // A command runs against its own owning context — its domain events dispatch there, and
        // any command it nests resolves there. The owning context is known at generation time from
        // the handler's own declared module, so it is baked in here rather than resolved by
        // searching every context for one that owns the command.
        val processorArgs =
            if (handler is CommandHandlerDefinition)
                CodeBlock.of(
                    "%L, owningContextFor(%L), handlerCreator",
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
                    .addParameter("contexts", contextsClassName)
                    .addParameter("transactionManager", config.transactionManagerClass)
                    .addParameter(middlewareListParameter())
                    .addParameter(appScopeParameter())
                    .addParameter(outboxParameter())
                    .addParameter(inboxParameter())
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
            .addProperty(
                PropertySpec.builder("contexts", contextsClassName)
                    .initializer("contexts")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addFunction(buildContextBuildingConstructor(factoryClassNames))
            .addFunction(buildLoaderConstructor(dependenciesClassName, factoryClassNames))
    }

    private fun factoryName(context: String): String =
        factoryPropertyName(context, config.handlerFactoryName)

    /**
     * Builds this bus's bounded contexts and applies `configure` to them, before the bus exists.
     */
    private fun buildContextBuildingConstructor(
        factoryClassNames: Map<String, ClassName>
    ): FunSpec {
        val factoryNames = factoryClassNames.keys.map { factoryName(it) }

        return FunSpec.constructorBuilder()
            .addModifiers(KModifier.PRIVATE)
            .apply {
                factoryClassNames.forEach { (context, className) ->
                    addParameter(factoryName(context), className)
                }
            }
            .addParameter("transactionManager", config.transactionManagerClass)
            .addParameter(middlewareListParameter())
            .addParameter(appScopeParameter())
            .addParameter(outboxParameter())
            .addParameter(inboxParameter())
            .addParameter(configureParameter())
            .callThisConstructor(
                factoryNames.map { CodeBlock.of(it) } +
                    listOf(
                        CodeBlock.of(
                            "%T(%L).apply(configure)",
                            contextsClassName,
                            factoryNames.joinToString(", "),
                        ),
                        CodeBlock.of("transactionManager"),
                        CodeBlock.of("middleware"),
                        CodeBlock.of("appScope"),
                        CodeBlock.of("outbox"),
                        CodeBlock.of("inbox"),
                    )
            )
            .build()
    }

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
            .addParameter(inboxParameter())
            .addParameter(configureParameter())
            .callThisConstructor(
                factoryClassNames.values.map { CodeBlock.of("%T(loader)", it) } +
                    listOf(
                        CodeBlock.of("transactionManager"),
                        CodeBlock.of("middleware"),
                        CodeBlock.of("appScope"),
                        CodeBlock.of("outbox"),
                        CodeBlock.of("inbox"),
                        CodeBlock.of("configure"),
                    )
            )
            .build()
}
