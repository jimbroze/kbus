package com.jimbroze.kbus.generation.generators

import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition

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
internal fun contextIdentities(handlers: Set<HandlerDefinition>): List<String> {
    val modules =
        handlers.map { it.handlerData.module }.filter { it.isNotBlank() }.distinct().sorted()

    return listOf(DEFAULT_CONTEXT) + modules
}

/** `order-fulfilment` -> `orderFulfilment`, for properties and accessors. */
internal fun contextAccessorName(context: String): String =
    context
        .split('-', '_', '.')
        .mapIndexed { index, segment ->
            if (index == 0) segment.replaceFirstChar { it.lowercase() }
            else segment.replaceFirstChar { it.uppercase() }
        }
        .joinToString("")

/**
 * `order-fulfilment` -> `OrderFulfilment`, the prefix every per-context generated type carries.
 * Types generated per context must agree on this or generated code will not compile against itself.
 */
internal fun contextClassPrefix(context: String): String =
    contextAccessorName(context).replaceFirstChar { it.uppercase() }
