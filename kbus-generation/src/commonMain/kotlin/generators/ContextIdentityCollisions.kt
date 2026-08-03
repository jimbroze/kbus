package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.KSPLogger
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition

/**
 * Reports bounded context identities the generated names cannot tell apart, returning whether
 * generation may go ahead.
 *
 * Identities differing only in their separators share one set of generated names while staying
 * distinct contexts at runtime, so the generated code would not compile against itself. An identity
 * naming the default context is worse than a name clash: it is folded into the catch-all that owns
 * every unassigned handler, and the isolation it asked for disappears with nothing to report it.
 * Either way the author has to hear it against their own declaration, not as a duplicate
 * declaration in source they never wrote.
 */
internal fun reportContextIdentityCollisions(
    handlers: Set<HandlerDefinition>,
    logger: KSPLogger,
): Boolean {
    val (defaultAliases, distinctIdentities) =
        declaredContextIdentities(handlers).partition {
            contextAccessorName(it) == contextAccessorName(DEFAULT_CONTEXT)
        }

    defaultAliases.forEach { identity ->
        logger.error(
            "Bounded context identity '$identity' names the default context, which already owns " +
                "every handler declaring no identity of its own, so " +
                "${handlersDeclaring(handlers, identity)} would be folded into it and lose the " +
                "isolation the identity asks for. Declare a different identity."
        )
    }

    val collisions =
        distinctIdentities.groupBy { contextAccessorName(it) }.values.filter { it.size > 1 }

    collisions.forEach { collidingIdentities ->
        logger.error(
            "Bounded context identities ${collidingIdentities.joinToString(", ") { "'$it'" }} " +
                "generate the same names but stay distinct contexts at runtime. Declared by " +
                collidingIdentities.joinToString("; ") {
                    "'$it' on ${handlersDeclaring(handlers, it)}"
                } +
                ". Make them one identity, or make them differ by more than separators."
        )
    }

    return defaultAliases.isEmpty() && collisions.isEmpty()
}

private fun handlersDeclaring(handlers: Set<HandlerDefinition>, identity: String): String =
    handlers
        .filter { it.handlerData.module == identity }
        .map { it.handlerData.handlerClass.canonicalName }
        .sorted()
        .joinToString(", ")
