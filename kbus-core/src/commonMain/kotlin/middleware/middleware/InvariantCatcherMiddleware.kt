package com.jimbroze.kbus.core.middleware.middleware

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareHandler
import com.jimbroze.kbus.domain.InvalidInvariantException
import com.jimbroze.kbus.domain.InvariantCatchingMessage

class InvariantCatcherMiddleware : Middleware {
    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        if (message !is InvariantCatchingMessage<*>) return nextMiddleware(message)

        @Suppress("UNCHECKED_CAST")
        return try {
            nextMiddleware(message)
        } catch (e: InvalidInvariantException) {
            message.invariantFailure(message.handleException(e))
        }
            as TResult
    }
}
