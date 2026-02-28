package com.jimbroze.kbus.domain

import com.jimbroze.kbus.contracts.messages.event.Event

abstract class DomainEvent : Event()

interface DomainEventPublisher {
    suspend fun dispatch(event: DomainEvent)
}
