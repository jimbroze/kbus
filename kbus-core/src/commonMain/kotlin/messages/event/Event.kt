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

/** Throws the first exception encountered immediately, stopping subsequent handlers. */
abstract class FailFastDomainEvent : DomainEvent()

/**
 * Runs all handlers regardless of failures, then throws an AggregateException with all failures.
 */
abstract class ContinueAndAggregateDomainEvent : DomainEvent()

/**
 * Catches all exceptions and logs them without bubbling to the publisher. This is the default
 * behavior for domain event handlers when no error handling interface is applied.
 */
abstract class FireAndForgetDomainEvent : DomainEvent()
