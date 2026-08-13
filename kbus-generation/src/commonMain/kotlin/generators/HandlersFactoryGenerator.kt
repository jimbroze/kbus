package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.jimbroze.kbus.api.annotations.index.RequiredDependencies
import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.api.messages.event.Event
import com.jimbroze.kbus.api.messages.event.EventHandler
import com.jimbroze.kbus.api.messages.query.Query
import com.jimbroze.kbus.api.messages.query.QueryHandler
import com.jimbroze.kbus.application.messages.command.CommandDependencies
import com.jimbroze.kbus.core.registry.generation.GenerationHandlerFactory
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import com.jimbroze.kbus.generation.processing.dependencies.CommandDependency
import com.jimbroze.kbus.generation.processing.dependencies.ContextCommandsDependency
import com.jimbroze.kbus.generation.processing.dependencies.parameterName
import com.jimbroze.kbus.generation.processing.dependencies.parameterType
import com.jimbroze.kbus.generation.processing.handlers.CommandHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.EventHandlerKind
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.QueryHandlerDefinition
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.reflect.KClass

@Suppress("LongParameterList")
class HandlersFactoryGenerator(
    private val codeGenerator: CodeGenerator,
    @Suppress("unused") private val logger: KSPLogger,
    private val factoryClassName: String,
    private val dependenciesInterfaceName: String,
    private val handlersInterfaceName: String,
    private val commandExecutorClassName: String,
    private val packagePath: String,
) {
    /**
     * One factory per bounded context, each holding only that context's handlers. Isolation is
     * structural: a context has no way to build a handler another context owns.
     */
    fun generateClasses(handlers: Set<HandlerDefinition>, sourceFiles: List<KSFile>) {
        val handlersByContext = handlers.groupBy { contextOf(it) }
        contextIdentities(handlers).forEach { context ->
            generateClass(context, handlersByContext[context].orEmpty().toSet(), sourceFiles)
        }
    }

    private fun generateClass(
        context: String,
        handlers: Set<HandlerDefinition>,
        sourceFiles: List<KSFile>,
    ) {
        val prefix = contextClassPrefix(context)
        val className = prefix + factoryClassName
        val superClassName = ClassName(packagePath, prefix + handlersInterfaceName)
        val dependenciesClassName = ClassName(packagePath, dependenciesInterfaceName)

        val classBuilder =
            TypeSpec.classBuilder(className)
                .addSuperinterface(superClassName)
                .addSuperinterface(GenerationHandlerFactory::class)

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
            .addFunction(
                buildCommandsHandlersFor(
                    handlers.filterIsInstance<CommandHandlerDefinition>().toSet(),
                    context,
                )
            )
            .addFunction(
                buildQueriesHandlersFor(handlers.filterIsInstance<QueryHandlerDefinition>().toSet())
            )
            .addFunctions(buildEventHandlerLookups(handlers))
            .addFunction(
                buildCommandTypes(handlers.filterIsInstance<CommandHandlerDefinition>().toSet())
            )
            .addFunction(
                buildQueryTypes(handlers.filterIsInstance<QueryHandlerDefinition>().toSet())
            )

        handlers.forEach { addHandlerDefinition(classBuilder, it, context) }

        val file = FileSpec.builder(packagePath, className)
        file.addAnnotation(
            AnnotationSpec.builder(ClassName("kotlin", "Suppress"))
                .addMember("%S", "OPT_IN_USAGE")
                .addMember("%S", "OPT_IN_USAGE_ERROR")
                .build()
        )
        file.addType(classBuilder.build())
        file
            .build()
            .writeTo(codeGenerator, Dependencies(true, sources = sourceFiles.toTypedArray()))
    }

    private fun buildCommandsHandlersFor(
        handlers: Set<CommandHandlerDefinition>,
        context: String,
    ): FunSpec {
        val tResult =
            TypeVariableName("TResult", ClassName("com.jimbroze.kbus.api.result", "KBusResult"))
        val tCommand =
            TypeVariableName("TCommand", Command::class.asClassName().parameterizedBy(tResult))
        val returnType =
            CommandHandler::class.asClassName()
                .parameterizedBy(tCommand, tResult)
                .copy(nullable = true)

        val codeBlock =
            CodeBlock.builder()
                .addStatement("@Suppress(%S)", "UNCHECKED_CAST")
                .add("return when (command) {\n")
                .indent()

        for (handler in handlers) {
            val handlerName = handler.handlerData.nameAsDependency
            val commandClass = handler.handlerData.messageClass
            val arguments =
                if (contextCommandsTypeOf(handler) != null)
                    "commandDependencies, ${contextClassPrefix(context)}$commandExecutorClassName" +
                        "(commandDependencies.commandExecutor)"
                else "commandDependencies"
            codeBlock.addStatement("is %T -> this.$handlerName($arguments)", commandClass)
        }

        codeBlock.addStatement("else -> null").unindent().add("} as %T", returnType)

        val functionBuilder =
            FunSpec.builder("handlerFor")
                .addModifiers(KModifier.OVERRIDE)
                .addTypeVariables(listOf(tCommand, tResult))
                .returns(returnType)
                .addParameter("command", tCommand)
                .addParameter("commandDependencies", CommandDependencies::class)
                .addCode(codeBlock.build())

        return functionBuilder.build()
    }

    // TODO make more polymorphic?
    private fun buildQueriesHandlersFor(handlers: Set<QueryHandlerDefinition>): FunSpec {
        val tResult =
            TypeVariableName("TResult", ClassName("com.jimbroze.kbus.api.result", "KBusResult"))
        val tQuery = TypeVariableName("TQuery", Query::class.asClassName().parameterizedBy(tResult))
        val returnType =
            QueryHandler::class.asClassName().parameterizedBy(tQuery, tResult).copy(nullable = true)

        val codeBlock =
            CodeBlock.builder()
                .addStatement("@Suppress(%S)", "UNCHECKED_CAST")
                .add("return when (query) {\n")
                .indent()

        for (handler in handlers) {
            val handlerName = handler.handlerData.nameAsDependency
            val queryClass = handler.handlerData.messageClass
            codeBlock.addStatement("is %T -> this.$handlerName()", queryClass)
        }

        codeBlock.addStatement("else -> null").unindent().add("} as %T", returnType)

        val functionBuilder =
            FunSpec.builder("handlerFor")
                .addModifiers(KModifier.OVERRIDE)
                .addTypeVariables(listOf(tQuery, tResult))
                .returns(returnType)
                .addParameter("query", tQuery)
                .addCode(codeBlock.build())

        return functionBuilder.build()
    }

    private fun buildEventHandlerLookups(handlers: Set<HandlerDefinition>): List<FunSpec> {
        val eventHandlersByKind =
            handlers.filterIsInstance<EventHandlerDefinition>().groupBy { it.kind }

        fun ofKind(kind: EventHandlerKind) = eventHandlersByKind[kind].orEmpty().toSet()

        return listOf(
            buildEventHandlerFor(
                ofKind(EventHandlerKind.INTEGRATION),
                functionName = "eventHandler",
                handlerSuperType = EventHandler::class.asClassName(),
                eventSuperType = Event::class.asClassName(),
            ),
            buildEventHandlerFor(
                ofKind(EventHandlerKind.DOMAIN),
                functionName = "domainEventHandler",
                handlerSuperType = DomainEventHandler::class.asClassName(),
                eventSuperType = DomainEvent::class.asClassName(),
            ),
        )
    }

    /**
     * Each event kind gets its own lookup, so the handler kind a caller asks for is the kind it can
     * get back — the domain path never sees a handler generated for an integration event.
     */
    private fun buildEventHandlerFor(
        handlers: Set<EventHandlerDefinition>,
        functionName: String,
        handlerSuperType: ClassName,
        eventSuperType: ClassName,
    ): FunSpec {
        val tEvent = TypeVariableName("TEvent", eventSuperType)

        val handlerClassType =
            KClass::class.asClassName().parameterizedBy(handlerSuperType.parameterizedBy(tEvent))
        val returnType = handlerSuperType.parameterizedBy(tEvent).copy(nullable = true)

        val codeBlock =
            CodeBlock.builder()
                .addStatement("@Suppress(%S)", "UNCHECKED_CAST")
                .add("return when (handlerClass) {\n")
                .indent()

        for (handler in handlers) {
            val handlerName = handler.handlerData.nameAsDependency
            val arguments = handler.functionParameters.joinToString(", ") { it.name }
            codeBlock.addStatement(
                "%T::class -> this.$handlerName($arguments)",
                handler.handlerData.handlerClass,
            )
        }

        codeBlock.addStatement("else -> null").unindent().add("} as %T", returnType)

        return FunSpec.builder(functionName)
            .addModifiers(KModifier.OVERRIDE)
            .addTypeVariables(listOf(tEvent))
            .addParameter("handlerClass", handlerClassType)
            .addParameter(
                RequiredDependencies.HANDLER_ONLY.parameterName,
                RequiredDependencies.HANDLER_ONLY.parameterType,
            )
            .returns(returnType)
            .addCode(codeBlock.build())
            .build()
    }

    private fun buildCommandTypes(handlers: Set<CommandHandlerDefinition>): FunSpec =
        buildMessageTypes("commandTypes", Command::class.asClassName(), handlers)

    private fun buildQueryTypes(handlers: Set<QueryHandlerDefinition>): FunSpec =
        buildMessageTypes("queryTypes", Query::class.asClassName(), handlers)

    private fun buildMessageTypes(
        functionName: String,
        messageClassName: ClassName,
        handlers: Set<HandlerDefinition>,
    ): FunSpec {
        val returnType =
            SET.parameterizedBy(
                KClass::class.asClassName()
                    .parameterizedBy(
                        WildcardTypeName.producerOf(messageClassName.parameterizedBy(STAR))
                    )
            )

        val messageClasses = handlers.map { CodeBlock.of("%T::class", it.handlerData.messageClass) }

        return FunSpec.builder(functionName)
            .addModifiers(KModifier.OVERRIDE)
            .returns(returnType)
            .addCode(CodeBlock.of("return %L", messageClasses.joinToCode(", ", "setOf(", ")")))
            .build()
    }

    private fun addHandlerDefinition(
        classBuilder: TypeSpec.Builder,
        handler: HandlerDefinition,
        context: String,
    ) {
        val returnType = handler.handlerData.handlerClass

        val subDependencyArgs =
            handler.handlerData.topLevelDependencies.joinToString(", ") {
                when (it) {
                    is ContextCommandsDependency -> contextCommandsParameterName(context)
                    is CommandDependency -> it.accessReferenceIn(handler.suppliedDependencies)
                    else -> "dependencies.${it.accessReferenceIn(handler.suppliedDependencies)}"
                }
            }

        val functionBuilder =
            FunSpec.builder(handler.handlerData.nameAsDependency)
                .addModifiers(KModifier.OVERRIDE)
                .returns(returnType)
                .addStatement("return %T($subDependencyArgs)", returnType)

        for (constructorParameter in handler.functionParameters) {
            functionBuilder.addParameter(constructorParameter.name, constructorParameter.typeRef)
        }
        contextCommandsTypeOf(handler)?.let { commandsType ->
            functionBuilder.addParameter(contextCommandsParameterName(context), commandsType)
        }

        classBuilder.addFunction(functionBuilder.build())
    }
}
