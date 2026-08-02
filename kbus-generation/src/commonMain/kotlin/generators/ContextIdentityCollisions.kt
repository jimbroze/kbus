package com.jimbroze.kbus.generation.generators

import com.google.devtools.ksp.processing.KSPLogger
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition

/**
 * Reports bounded context identities the generated names cannot tell apart, returning whether
 * generation may go ahead.
 *
 * Identities differing only in their separators share one set of generated names while staying
 * distinct contexts at runtime, so the generated code would not compile against itself. The author
 * of the identities has to hear that against their own declaration, not as a duplicate-declaration
 * error in source they never wrote.
 */
internal fun reportContextIdentityCollisions(
    handlers: Set<HandlerDefinition>,
    logger: KSPLogger,
): Boolean {
    val collisions =
        declaredContextIdentities(handlers)
            .groupBy { contextAccessorName(it) }
            .values
            .filter { it.size > 1 }

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

    return collisions.isEmpty()
}

private fun handlersDeclaring(handlers: Set<HandlerDefinition>, identity: String): String =
    handlers
        .filter { it.handlerData.module == identity }
        .map { it.handlerData.handlerClass.canonicalName }
        .sorted()
        .joinToString(", ")
