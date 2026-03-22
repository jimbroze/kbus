package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.bus.CanDispatchIntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.domain.DomainEvent

abstract class DomainEventHandler<TEvent : DomainEvent> :
    EventHandler<TEvent>, CanDispatchIntegrationEvent() {
    abstract override suspend fun handle(message: TEvent)
}

abstract class DispatchSynchronously<TEvent : DomainEvent> : DomainEventHandler<TEvent>()

abstract class DispatchAsynchronously<TEvent : DomainEvent> : DomainEventHandler<TEvent>()

abstract class DispatchImmediatelyInTransaction<TEvent : DomainEvent> :
    DispatchSynchronously<TEvent>()

abstract class DispatchAtEndOfTransaction<TEvent : DomainEvent> : DispatchSynchronously<TEvent>()

abstract class DispatchAfterTransaction<TEvent : DomainEvent> : DispatchAsynchronously<TEvent>()
