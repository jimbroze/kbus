package com.jimbroze.kbus.domain.event

interface DomainEventPublisher {
    suspend fun publish(event: DomainEvent)
}
