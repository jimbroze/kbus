package com.jimbroze.kbus.domain.event

import com.jimbroze.kbus.api.messages.event.EventHandler

abstract class DomainEventHandler<TEvent : DomainEvent> : EventHandler<TEvent> {
    open val dispatchTiming: DispatchTiming = DispatchTiming.AfterTransaction

    abstract override suspend fun handle(message: TEvent)
}
