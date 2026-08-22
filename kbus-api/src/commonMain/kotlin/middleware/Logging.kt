package com.jimbroze.kbus.api.middleware

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
