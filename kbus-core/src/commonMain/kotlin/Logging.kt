package com.jimbroze.kbus.core

interface LogLevel {
    val level: String
}

interface Logger {
    fun log(level: LogLevel, message: String, exception: Throwable?)
}

interface LoggingMessage {
    val messageType: String
    val finiteVerb: String
        get() = "handle"

    val presentVerb: String
        get() = "handling"

    val pastVerb: String
        get() = "handled"

    fun preHandleLog(): String {
        val handling = presentVerb.replaceFirstChar(Char::titlecase)
        val name = this.toString()
        return "$handling $messageType <$name>"
    }

    fun postHandleLog(): String {
        val handled = pastVerb
        val name = this.toString()
        return "Successfully $handled $messageType <$name>"
    }

    fun errorLog(): String {
        val handling = presentVerb
        val name = this.toString()
        return "Failed $handling $messageType <$name>"
    }
}

interface LoggingCommand : LoggingMessage {
    override val finiteVerb: String
        get() = "execute"

    override val presentVerb: String
        get() = "executing"

    override val pastVerb: String
        get() = "executed"
}

interface LoggingQuery : LoggingMessage {
    override val finiteVerb: String
        get() = "process"

    override val presentVerb: String
        get() = "processing"

    override val pastVerb: String
        get() = "processed"
}

interface LoggingEvent : LoggingMessage {
    override val finiteVerb: String
        get() = "dispatch"

    override val presentVerb: String
        get() = "dispatching"

    override val pastVerb: String
        get() = "dispatched"
}

class MessageLogger(
    private val logger: Logger,
    private val preDispatchLevel: LogLevel,
    private val postDispatchLevel: LogLevel,
    private val errorLevel: LogLevel,
) : Middleware {
    override suspend fun <TMessage : Message> handle(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage>,
    ): Any? {
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
