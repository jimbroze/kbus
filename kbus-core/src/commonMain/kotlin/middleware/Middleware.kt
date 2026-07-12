package com.jimbroze.kbus.core.middleware

import com.jimbroze.kbus.contracts.common.Message

typealias MiddlewareHandler<TMessage, TResult> = suspend (TMessage) -> TResult

/**
 * Marker interface for per-invocation context passed to [Middleware.handle]. Carries no data yet;
 * it exists so future invocation-scoped data (e.g. correlation ids) can be added without another
 * change to the `handle` signature.
 */
interface MiddlewareInvocationContext

object DefaultMiddlewareInvocationContext : MiddlewareInvocationContext

interface Middleware {
    suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        context: MiddlewareInvocationContext,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult
}

fun <TMessage : Message, TResult> createMiddlewareChain(
    finalHandler: MiddlewareHandler<TMessage, TResult>,
    middlewares: List<Middleware>,
    context: MiddlewareInvocationContext = DefaultMiddlewareInvocationContext,
): MiddlewareHandler<TMessage, TResult> {
    var lastHandler = finalHandler
    middlewares.reversed().forEach {
        val currentHandler = lastHandler
        lastHandler = { message: TMessage -> it.handle(message, context, currentHandler) }
    }

    return lastHandler
}
