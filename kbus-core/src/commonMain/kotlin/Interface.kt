package com.jimbroze.kbus.core

import kotlin.reflect.KClass

abstract class Message {
    abstract val messageType: String

    final override fun toString(): String = this::class.simpleName ?: ""
}

interface MessageHandler<TMessage : Message> {
    suspend fun handle(message: TMessage): Any?
}

class MissingHandlerException(
    message: String = "The requested message handler could not be found"
) : Exception(message) {
    constructor(
        messageCls: KClass<out Message>
    ) : this("A handler could not be found for the message '$messageCls'")
}
