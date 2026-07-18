package com.jimbroze.kbus.contracts.messages.event

interface IntegrationEventPublisher {
    suspend fun publish(events: List<IntegrationEvent>)
}
