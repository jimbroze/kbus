package com.jimbroze.kbus.core.middleware

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher

typealias MiddlewareHandler<TMessage, TResult> = suspend (TMessage) -> TResult

/**
 * Per-invocation context passed to [Middleware.handle]. Exists so invocation-scoped data (e.g.
 * [integrationEventPublisher], correlation ids) can be added without another change to the `handle`
 * signature.
 */
interface MiddlewareInvocationContext {
    val integrationEventPublisher: IntegrationEventPublisher
}

/**
 * Whether a middleware re-runs for a command executed from inside another command's invocation.
 * Governs nothing else — event dispatch is its own entry point and always runs the full chain.
 *
 * There is no default: only a middleware's author knows whether re-entering it is safe.
 */
enum class MiddlewareScope {
    EntryPointOnly,
    EveryCommand,
}

interface Middleware {
    val scope: MiddlewareScope

    suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        context: MiddlewareInvocationContext,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult
}

fun <TMessage : Message, TResult> createMiddlewareChain(
    finalHandler: MiddlewareHandler<TMessage, TResult>,
    middlewares: List<Middleware>,
    context: MiddlewareInvocationContext,
): MiddlewareHandler<TMessage, TResult> {
    var lastHandler = finalHandler
    middlewares.reversed().forEach {
        val currentHandler = lastHandler
        lastHandler = { message: TMessage -> it.handle(message, context, currentHandler) }
    }

    return lastHandler
}
