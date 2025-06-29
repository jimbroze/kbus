package com.jimbroze.kbus.core

import com.jimbroze.kbus.core.domain.DomainEvent

abstract class Event : Message() {
    override val messageType: String = "event"
}

interface EventHandler<TEvent : Event> : MessageHandler<TEvent> {
    override suspend fun handle(message: TEvent)
}

interface DomainEventHandler<TEvent : DomainEvent> : EventHandler<TEvent> {
    override suspend fun handle(message: TEvent)
}

interface DispatchImmediately<TEvent : DomainEvent> : DomainEventHandler<TEvent>

interface DispatchAfterPrimaryWork<TEvent : DomainEvent> : DomainEventHandler<TEvent>

interface DispatchAfterCommit<TEvent : DomainEvent> : EventHandler<TEvent>
