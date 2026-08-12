package com.jimbroze.kbus.core.middleware

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.contracts.middleware.LoggingMessage
import com.jimbroze.kbus.core.infrastructure.logging.LogLevel
import com.jimbroze.kbus.core.infrastructure.logging.Logger
import com.jimbroze.kbus.core.middleware.infrastructure.Middleware
import com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareHandler
import com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareInvocationContext
import com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareScope

class LoggingMiddleware(
    private val logger: Logger,
    private val preDispatchLevel: LogLevel,
    private val postDispatchLevel: LogLevel,
    private val errorLevel: LogLevel,
) : Middleware {
    /** Observational: a nested command is a real message being handled. */
    override val scope = MiddlewareScope.EveryCommand

    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        context: MiddlewareInvocationContext,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        if (message !is LoggingMessage) return nextMiddleware(message)

        logger.log(preDispatchLevel, message.preHandleLog(), null)

        @Suppress("TooGenericExceptionCaught")
        return try {
            val result = nextMiddleware(message)
            logger.log(postDispatchLevel, message.postHandleLog(), null)
            result
        } catch (ex: Throwable) {
            logger.log(errorLevel, message.errorLog(), ex)
            throw ex
        }
    }
}
