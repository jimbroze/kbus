package com.jimbroze.kbus.core.domain

import com.jimbroze.kbus.core.Event

abstract class DomainEvent : Event()

interface DomainEventPublisher {
    suspend fun dispatch(event: DomainEvent)
}
