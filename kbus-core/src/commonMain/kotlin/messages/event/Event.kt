package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.bus.CanDispatchIntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.domain.DomainEvent

abstract class DomainEventHandler<TEvent : DomainEvent> :
    EventHandler<TEvent>, CanDispatchIntegrationEvent() {
    abstract override suspend fun handle(message: TEvent)
}

// TODO should these apply to integration events too?
abstract class DispatchImmediately<TEvent : DomainEvent> : DomainEventHandler<TEvent>()

abstract class DispatchAfterPrimaryWork<TEvent : DomainEvent> : DomainEventHandler<TEvent>()

abstract class DispatchAfterCommit<TEvent : DomainEvent> : DomainEventHandler<TEvent>()
