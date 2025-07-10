package com.jimbroze.kbus.core

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
