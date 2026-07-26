package com.jimbroze.kbus.contracts.messages.event

/**
 * A place [EventEnvelope]s can be routed to: local dispatch today, external transports later.
 * [appliesTo] filters which events this destination wants; [deliver] receives only those.
 *
 * [deliver] must be durable on return, or throw. A throw means "not acked" — the caller (an
 * `EnvelopeRelay`, shared by the outbox drain, the immediate publisher, the outbox poller, and
 * every inbox pump) leaves the entry unpublished for its own retry loop. This is the whole ack
 * mechanism; there is no separate acknowledgement step. A destination fronted by an inbox turns
 * *deliver* into *save durably and return* — dispatch to handlers happens later, off a
 * separately-pumped store, and only that pump's ack decides whether the entry is redelivered.
 */
interface EventDestination {
    val name: String

    fun appliesTo(event: IntegrationEvent): Boolean

    suspend fun deliver(envelopes: List<EventEnvelope>)
}
