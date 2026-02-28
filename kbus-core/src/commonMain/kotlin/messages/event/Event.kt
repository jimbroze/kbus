package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.core.bus.CanDispatchIntegrationEvent
import com.jimbroze.kbus.core.common.Message
import com.jimbroze.kbus.core.common.VoidReturningMessageHandler
import com.jimbroze.kbus.core.domain.DomainEvent

abstract class Event : Message {
    override val messageType: String = "event"

    final override fun toString(): String = this::class.simpleName ?: "Event"
}

interface EventHandler<TEvent : Event> : VoidReturningMessageHandler<TEvent> {
    override suspend fun handle(message: TEvent)
}

abstract class DomainEventHandler<TEvent : DomainEvent> :
    EventHandler<TEvent>, CanDispatchIntegrationEvent() {
    abstract override suspend fun handle(message: TEvent)
}

// TODO should these apply to integration events too?
abstract class DispatchImmediately<TEvent : DomainEvent> : DomainEventHandler<TEvent>()

abstract class DispatchAfterPrimaryWork<TEvent : DomainEvent> : DomainEventHandler<TEvent>()

abstract class DispatchAfterCommit<TEvent : DomainEvent> : DomainEventHandler<TEvent>()

abstract class IntegrationEvent : Event()

interface IntegrationEventHandler<TEvent : IntegrationEvent> : EventHandler<TEvent> {
    override suspend fun handle(message: TEvent)
}
