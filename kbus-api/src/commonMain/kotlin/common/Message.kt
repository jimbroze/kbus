package com.jimbroze.kbus.api.common

import com.jimbroze.kbus.api.result.KBusResult
import kotlin.reflect.KClass

interface Message {
    val messageType: String

    override fun toString(): String
}

interface ResultReturningMessage<TResult : KBusResult> : Message

interface MessageHandler<TMessage : Message> {
    suspend fun handle(message: TMessage): Any?
}

interface VoidReturningMessageHandler<TMessage : Message> : MessageHandler<TMessage> {
    override suspend fun handle(message: TMessage): Unit
}

class MissingHandlerException(
    message: String = "The requested message handler could not be found"
) : Exception(message) {
    constructor(
        messageCls: KClass<out Message>
    ) : this("A handler could not be found for the message '$messageCls'")
}

/**
 * Thrown when a command or query resolves against more than one bounded context. Commands and
 * queries are single-owner by contract, so this signals a wiring bug rather than something to
 * resolve by ordering.
 */
class AmbiguousHandlerException(
    message: String = "The requested message handler was found in more than one bounded context"
) : Exception(message) {
    constructor(
        messageCls: KClass<out Message>,
        contextIds: List<String>,
    ) : this(
        "A handler for message '$messageCls' was found in more than one bounded context: " +
            "$contextIds. Commands and queries must be owned by exactly one context."
    )
}
