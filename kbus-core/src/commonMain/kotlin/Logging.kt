package com.jimbroze.kbus.core

interface Logger {
    fun info(message: String)

    fun error(message: String)
}

interface LoggingMessage {
    val messageType: String
    val finiteVerb: String get() = "handle"
    val presentVerb: String get() = "handling"
    val pastVerb: String get() = "handled"

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
    override val finiteVerb: String get() = "execute"
    override val presentVerb: String get() = "executing"
    override val pastVerb: String get() = "executed"
}

interface LoggingQuery : LoggingMessage {
    override val finiteVerb: String get() = "process"
    override val presentVerb: String get() = "processing"
    override val pastVerb: String get() = "processed"
}

interface LoggingEvent : LoggingMessage {
    override val finiteVerb: String get() = "dispatch"
    override val presentVerb: String get() = "dispatching"
    override val pastVerb: String get() = "dispatched"
}

class MessageLogger(private val logger: Logger) : Middleware {
    override suspend fun <TMessage : Message> handle(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage>,
    ): Any? {
        if (message !is LoggingMessage) return nextMiddleware(message)

        logger.info(message.preHandleLog())

        return try {
            val result = nextMiddleware(message)
            logger.info(message.postHandleLog())
            result
        } catch (ex: Exception) { // TODO catch runtime & throwable
            logger.error(message.errorLog())
            throw ex
        }
    }
}
