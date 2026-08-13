package com.jimbroze.kbus.generation.generators

import com.jimbroze.kbus.application.messages.command.NestedCommandExecutor
import com.jimbroze.kbus.generation.processing.dependencies.ContextCommandsDependency
import com.jimbroze.kbus.generation.processing.handlers.CommandHandlerDefinition
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName

/** The context handlers declaring no identity of their own belong to. */
internal const val DEFAULT_CONTEXT = "default"

/** The context a handler belongs to, with an unassigned module mapped to [DEFAULT_CONTEXT]. */
internal fun contextOf(handler: HandlerDefinition): String =
    handler.handlerData.module.ifBlank { DEFAULT_CONTEXT }

/** The identity a context is declared under, `""` for [DEFAULT_CONTEXT]. */
internal fun contextIdentity(context: String): String =
    if (context == DEFAULT_CONTEXT) "" else context

/**
 * The bounded contexts a bus wires up: every distinct identity stamped on any handler — command,
 * query or event — plus the default context, which owns every handler whose producing module
 * declared none. A context defining only commands or only domain handlers still needs its own
 * entry, or its handlers would be unreachable by owner lookup.
 */
internal fun contextIdentities(handlers: Set<HandlerDefinition>): List<String> =
    listOf(DEFAULT_CONTEXT) + declaredContextIdentities(handlers)

/** The identities handlers were stamped with, excluding the default context nothing declares. */
internal fun declaredContextIdentities(handlers: Set<HandlerDefinition>): List<String> =
    handlers.map { it.handlerData.module }.filter { it.isNotBlank() }.distinct().sorted()

/** `order-fulfilment` -> `orderFulfilment`, for properties and accessors. */
internal fun contextAccessorName(context: String): String =
    context
        .split('-', '_', '.')
        .mapIndexed { index, segment ->
            if (index == 0) segment.replaceFirstChar { it.lowercase() }
            else segment.replaceFirstChar { it.uppercase() }
        }
        .joinToString("")

/** `order-fulfilment` -> `OrderFulfilment`, the prefix every per-context generated type carries. */
internal fun contextClassPrefix(context: String): String =
    contextAccessorName(context).replaceFirstChar { it.uppercase() }

/** The contexts owning at least one command, and so having a typed executor generated for them. */
internal fun contextsWithCommands(handlers: Set<HandlerDefinition>): Set<String> =
    handlers.filterIsInstance<CommandHandlerDefinition>().map { contextOf(it) }.toSet()

/**
 * The type a context's commands are handed to a handler as. Contexts owning no command have no
 * typed view generated, and fall back to the untyped executor every context can supply.
 */
internal fun contextCommandsType(
    context: String,
    handlers: Set<HandlerDefinition>,
    packagePath: String,
    executorClassName: String,
): TypeName =
    if (context in contextsWithCommands(handlers))
        ClassName(packagePath, contextClassPrefix(context) + executorClassName)
    else NestedCommandExecutor::class.asClassName()

/** The parameter a handler's commands arrive under, named for the context that owns them. */
internal fun contextCommandsParameterName(context: String): String =
    "${contextAccessorName(context)}Commands"

/**
 * The type [handler] asks for its context's commands as, or null if it asks for none. This is the
 * interface the handler declared rather than the concrete executor, so a module generating against
 * a handler it did not declare names a type it can already see.
 */
internal fun contextCommandsTypeOf(handler: HandlerDefinition): TypeName? =
    handler.handlerData.topLevelDependencies
        .filterIsInstance<ContextCommandsDependency>()
        .firstOrNull()
        ?.typeName
