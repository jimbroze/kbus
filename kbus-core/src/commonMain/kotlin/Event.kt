package com.jimbroze.kbus.core

abstract class Event : Message() {
    override val messageType: String = "event"
}

interface EventHandler<TEvent : Event> : MessageHandler<TEvent> {
    override suspend fun handle(message: TEvent)
}
