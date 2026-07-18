package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent

interface IntegrationEventPublisher {
    suspend fun publish(events: List<IntegrationEvent>)
}
