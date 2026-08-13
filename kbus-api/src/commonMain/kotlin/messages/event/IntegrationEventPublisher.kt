package com.jimbroze.kbus.api.messages.event

interface IntegrationEventPublisher {
    suspend fun publish(events: List<IntegrationEvent>)
}
