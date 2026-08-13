package com.jimbroze.kbus.core.boundedcontext.inbox

import com.jimbroze.kbus.api.inbox.InboxStore
import com.jimbroze.kbus.api.messages.event.EventDestination
import com.jimbroze.kbus.api.messages.event.EventEnvelope
import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.messages.event.relay.EnvelopeRelay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Decorates [inner] with a durable, per-context inbox. [deliver] collapses to *save durably and
 * return*: the router acks on the save, and whatever [inner] does happens later, off [store], via
 * [drain] (one opportunistic tick) and [pump] (the scheduled loop).
 *
 * Both read from [store] rather than from the batch just saved, so a tick also sweeps up what a
 * previous failed one left behind. [inner] is delivered one entry at a time, which is what makes
 * the ack per-entry: a throwing handler leaves that envelope unacked and not its siblings.
 *
 * Racing a [drain] against a [pump] tick is safe within a process; cross-process overlap remains
 * [store]'s to resolve via [InboxStore.fetchPending], as for the outbox.
 *
 * A [store] failure propagates out of [deliver], leaving the producer's outbox to retry — the only
 * remaining way an inboxed context can un-ack an envelope.
 */
class EventInbox
internal constructor(
    private val inner: EventDestination,
    private val store: InboxStore,
    private val pumpScope: CoroutineScope,
    private val tuning: InboxTuning,
) : EventDestination {
    override val name: String
        get() = inner.name

    override fun appliesTo(event: IntegrationEvent): Boolean = inner.appliesTo(event)

    private val relay =
        EnvelopeRelay(
            fetch = store::fetchPending,
            deliver = { inner.deliver(listOf(it)) },
            ack = store::markConsumed,
            maxConcurrentDeliveries = tuning.maxConcurrentDeliveries,
        )

    override suspend fun deliver(envelopes: List<EventEnvelope>) {
        if (envelopes.isEmpty()) return
        store.save(envelopes)
        if (tuning.opportunisticDispatch) pumpScope.launch { drain() }
    }

    internal suspend fun drain() = relay.pollOnce(tuning.batchSize)

    internal suspend fun pump(): Nothing = relay.poll(tuning.batchSize, tuning.pollInterval)
}
