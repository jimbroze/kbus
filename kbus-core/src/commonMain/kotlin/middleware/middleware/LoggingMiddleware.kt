package com.jimbroze.kbus.core.middleware.middleware

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.contracts.middleware.LoggingMessage
import com.jimbroze.kbus.core.infrastructure.logging.LogLevel
import com.jimbroze.kbus.core.infrastructure.logging.Logger
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareHandler

class LoggingMiddleware(
    private val logger: Logger,
    private val preDispatchLevel: LogLevel,
    private val postDispatchLevel: LogLevel,
    private val errorLevel: LogLevel,
) : Middleware {
    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
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
