package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.EventDestination
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope

/**
 * The seam between publish and dispatch: every integration event, from every publish path, passes
 * through [route] before reaching any handler. Owns the [observerRegistry] so [observe] fires
 * exactly once per event, before fan-out, regardless of how many destinations exist.
 *
 * Per envelope: emit to observers, then attempt **every** accepting [EventDestination], collecting
 * failures rather than stopping at the first one — healthy destinations get the event immediately
 * and only dedupe a duplicate on retry, instead of waiting for a sick one to recover. If any
 * destination failed, throws [MultipleException] so the caller (the outbox) leaves the entry
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
            val accepted = envelopes.filter { destination.accepts(it.event) }
            if (accepted.isEmpty()) return@forEach

            @Suppress("TooGenericExceptionCaught")
            try {
                destination.deliver(accepted)
            } catch (e: Exception) {
                failures.add(e)
            }
        }

        if (failures.isNotEmpty()) throw MultipleException(failures)
    }
}
