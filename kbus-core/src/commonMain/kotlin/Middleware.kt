package com.jimbroze.kbus.core

typealias MiddlewareHandler<TMessage, TResult> = suspend (TMessage) -> TResult

interface Middleware {
    suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult
}

fun <TMessage : Message, TResult> createMiddlewareChain(
    finalHandler: MiddlewareHandler<TMessage, TResult>,
    middlewares: List<Middleware>,
): MiddlewareHandler<TMessage, TResult> {
    var lastHandler = finalHandler
    middlewares.reversed().forEach {
        val currentHandler = lastHandler
        lastHandler = { message: TMessage -> it.handle(message, currentHandler) }
    }

    return lastHandler
}
