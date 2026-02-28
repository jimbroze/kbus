package com.jimbroze.kbus.core.domain

import com.jimbroze.kbus.core.messages.event.Event

// FIXME move Base classes to contracts module (Rename annotations module)
// Probably want to move handler base classes
abstract class DomainEvent : Event()

interface DomainEventPublisher {
    suspend fun dispatch(event: DomainEvent)
}
