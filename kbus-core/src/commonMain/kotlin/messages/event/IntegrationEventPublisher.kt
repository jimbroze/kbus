package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.bus.BusAccess
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent

interface IntegrationEventPublisher {
    suspend fun publish(events: List<IntegrationEvent>)
}

/** [BusAccess] that routes imperative `dispatch()` calls through [publisher]. */
internal class PublisherBusAccess(private val publisher: IntegrationEventPublisher) : BusAccess {
    override suspend fun <TEvent : IntegrationEvent> dispatch(event: TEvent) {
        publisher.publish(listOf(event))
    }
}
