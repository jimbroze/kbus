package com.jimbroze.kbus.domain.event

import com.jimbroze.kbus.contracts.bus.CanDispatchIntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.EventHandler

abstract class DomainEventHandler<TEvent : DomainEvent> :
    EventHandler<TEvent>, CanDispatchIntegrationEvent() {
    open val dispatchTiming: DispatchTiming = DispatchTiming.AfterTransaction

    abstract override suspend fun handle(message: TEvent)
}
