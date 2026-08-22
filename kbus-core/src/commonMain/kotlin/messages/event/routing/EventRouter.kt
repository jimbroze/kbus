package com.jimbroze.kbus.core.messages.event.routing

import com.jimbroze.kbus.core.messages.event.dispatch.IntegrationEventObserverRegistry
import com.jimbroze.kbus.infrastructure.event.EventDestination
import com.jimbroze.kbus.infrastructure.event.EventEnvelope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
 * Every accepting [EventDestination] is attempted concurrently and failures are collected rather
 * than short-circuited, so one sick destination neither delays nor denies the healthy ones. Any
 * failure throws [AggregateException], leaving the entry unpublished for the poller; the
 * destinations that did succeed see the retry as a duplicate, which at-least-once already requires
 * them to tolerate.
 *
 * Concurrency is per destination, never within one: a destination receives its whole accepted batch
 * in a single [EventDestination.deliver] call, so ordering within a destination is its own to make.
 * A [CancellationException] is rethrown rather than collected, so a shutdown signal stays a
 * shutdown signal.
 */
class EventRouter(
    private val destinations: List<EventDestination>,
    val observerRegistry: IntegrationEventObserverRegistry = IntegrationEventObserverRegistry(),
) {
    suspend fun route(envelopes: List<EventEnvelope>) {
        envelopes.forEach { observerRegistry.emit(it.event) }

        val failures = coroutineScope {
            destinations
                .mapNotNull { destination ->
                    val acceptedEnvelopes = envelopes.filter { destination.appliesTo(it.event) }
                    if (acceptedEnvelopes.isEmpty()) return@mapNotNull null

                    async {
                        @Suppress("TooGenericExceptionCaught")
                        try {
                            destination.deliver(acceptedEnvelopes)
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            e
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }

        if (failures.isNotEmpty()) throw AggregateException(failures)
    }
}
