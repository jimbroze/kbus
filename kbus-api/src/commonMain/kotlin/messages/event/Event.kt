package com.jimbroze.kbus.api.messages.event

import com.jimbroze.kbus.api.common.Message
import com.jimbroze.kbus.api.common.VoidReturningMessageHandler

abstract class Event : Message {
    override val messageType: String = "event"

    final override fun toString(): String = this::class.simpleName ?: "Event"
}

interface EventHandler<TEvent : Event> : VoidReturningMessageHandler<TEvent> {
    override suspend fun handle(message: TEvent)
}

abstract class IntegrationEvent : Event() {
    open val concurrency: Concurrency = Concurrency.Concurrent
    open val errorStrategy: ErrorStrategy = ErrorStrategy.FireAndForget
}

interface IntegrationEventHandler<TEvent : IntegrationEvent> : EventHandler<TEvent> {
    override suspend fun handle(message: TEvent)
}
