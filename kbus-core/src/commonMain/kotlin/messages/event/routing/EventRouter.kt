package com.jimbroze.kbus.core.messages.event.routing

import com.jimbroze.kbus.contracts.messages.event.EventDestination
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.core.messages.event.dispatch.IntegrationEventObserverRegistry
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
 * Per envelope: emit to observers, then attempt **every** accepting [EventDestination]
 * concurrently, collecting failures rather than stopping at the first one — a slow or sick
 * destination neither delays nor denies delivery to the healthy ones, which only dedupe a duplicate
 * on retry. If any destination failed, throws [AggregateException] so the caller (the outbox)
 * leaves the entry unpublished for the poller to retry; destinations that succeeded will see that
 * retry as a duplicate, which is what at-least-once already requires them to tolerate.
 *
 * Concurrency is per destination, never within one: a destination receives its whole accepted batch
 * in a single [EventDestination.deliver] call, so ordering guarantees within a destination are its
 * own to make. A [CancellationException] from a destination is rethrown rather than collected —
 * dispatch commonly runs inside a cancellable coroutine (a launched publish, an inbox pump), and
 * wrapping a cancellation into an [AggregateException] would turn a normal shutdown signal into a
 * genuine, uncaught error. It cancels the in-flight siblings too, which is the point: the caller is
 * shutting down, and their entries stay unpublished for the poller.
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
