package com.jimbroze.kbus.domain.event

import com.jimbroze.kbus.contracts.messages.event.CanPublishIntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.EventHandler

abstract class DomainEventHandler<TEvent : DomainEvent> :
    EventHandler<TEvent>, CanPublishIntegrationEvent() {
    open val dispatchTiming: DispatchTiming = DispatchTiming.AfterTransaction

    abstract override suspend fun handle(message: TEvent)
}
