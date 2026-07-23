package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher

/** The no-outbox ingress: mints envelopes and routes them immediately, with no durability. */
class DirectPublisher(private val router: EventRouter) : IntegrationEventPublisher {
    override suspend fun publish(events: List<IntegrationEvent>) {
        router.route(events.map { EventEnvelope.of(it) })
    }
}
