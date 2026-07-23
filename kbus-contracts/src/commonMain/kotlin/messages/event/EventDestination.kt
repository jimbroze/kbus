package com.jimbroze.kbus.contracts.messages.event

/**
 * A place [EventEnvelope]s can be routed to: local dispatch today, external transports later.
 *
 * [deliver] must be durable on return, or throw. A throw means "not accepted" — the caller (the
 * outbox's `deliverAndMark`) leaves the entry unpublished for the poller to retry. This is the
 * whole ack mechanism; there is no separate acknowledgement step.
 */
interface EventDestination {
    val name: String

    fun accepts(event: IntegrationEvent): Boolean

    suspend fun deliver(envelopes: List<EventEnvelope>)
}
