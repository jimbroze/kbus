package com.jimbroze.kbus.core.messages.event.routing

import com.jimbroze.kbus.contracts.messages.event.EventDestination
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.core.messages.event.dispatch.IntegrationEventObserverRegistry

/**
 * The seam between publish and dispatch: every integration event, from every publish path, passes
 * through [route] before reaching any handler. Owns the [observerRegistry] so [observe] fires once
 * per routing attempt, before fan-out, regardless of how many destinations exist.
 *
 * Observation is **at-least-once**, not exactly-once: [route] re-emits on every call, and a failed
 * destination leaves the entry unpublished for the poller to re-route, so observers see a retried
 * event again. Exactly-once observe would require deduping on [EventEnvelope.id] against a durable
 * store — the same id-keyed machinery a consuming inbox will need.
 *
 * Per envelope: emit to observers, then attempt **every** accepting [EventDestination], collecting
 * failures rather than stopping at the first one — healthy destinations get the event immediately
 * and only dedupe a duplicate on retry, instead of waiting for a sick one to recover. If any
 * destination failed, throws [AggregateException] so the caller (the outbox) leaves the entry
 * unpublished for the poller to retry.
 */
class EventRouter(
    private val destinations: List<EventDestination>,
    val observerRegistry: IntegrationEventObserverRegistry = IntegrationEventObserverRegistry(),
) {
    suspend fun route(envelopes: List<EventEnvelope>) {
        envelopes.forEach { observerRegistry.emit(it.event) }

        val failures = mutableListOf<Exception>()
        destinations.forEach { destination ->
            val acceptedEnvelopes = envelopes.filter { destination.appliesTo(it.event) }
            if (acceptedEnvelopes.isEmpty()) return@forEach

            @Suppress("TooGenericExceptionCaught")
            try {
                destination.deliver(acceptedEnvelopes)
            } catch (e: Exception) {
                failures.add(e)
            }
        }

        if (failures.isNotEmpty()) throw AggregateException(failures)
    }
}
