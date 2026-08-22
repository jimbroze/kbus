package com.jimbroze.kbus.infrastructure.event

import com.jimbroze.kbus.api.messages.event.IntegrationEvent

/**
 * A place [EventEnvelope]s can be routed to: local dispatch today, external transports later.
 * [appliesTo] filters which events this destination wants; [deliver] receives only those.
 *
 * [deliver] must be durable on return, or throw. Returning normally acks every envelope passed in;
 * a throw acks none of them and the caller will redeliver. This is the whole ack mechanism — there
 * is no separate acknowledgement step. "Durable" is the destination's own definition: one that
 * saves the envelopes to a store it pumps separately may return as soon as they are saved, leaving
 * that pump to decide redelivery from then on.
 */
interface EventDestination {
    val name: String

    fun appliesTo(event: IntegrationEvent): Boolean

    suspend fun deliver(envelopes: List<EventEnvelope>)
}
