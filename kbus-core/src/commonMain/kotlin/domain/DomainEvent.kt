package com.jimbroze.kbus.core.domain

import com.jimbroze.kbus.core.messages.event.Event

abstract class DomainEvent : Event()

interface DomainEventPublisher {
    suspend fun dispatch(event: DomainEvent)
}
