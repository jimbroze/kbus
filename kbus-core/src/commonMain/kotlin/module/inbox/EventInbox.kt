package com.jimbroze.kbus.core.module.inbox

import com.jimbroze.kbus.contracts.inbox.InboxStore
import com.jimbroze.kbus.contracts.messages.event.EventDestination
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.messages.event.relay.EnvelopeRelay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Decorates [inner] with a durable, per-context inbox. [deliver] collapses to *save durably and
 * return* — the router acks immediately, whatever [inner] does happens later, off [store], via
 * [drain] (an opportunistic single tick) and [pump] (the scheduled loop). [inner] and the router
 * are otherwise untouched: this is a decorator, not a branch inside [inner].
 *
 * The drain reads from [store], never from the batch just saved — the store is the only truth about
 * what is still pending, so a drain also sweeps up whatever a previous failed drain left behind,
 * and a re-saved duplicate is filtered by [InboxStore.fetchPending] rather than by ad-hoc logic
 * here. [inner]'s `deliver` is called once per entry (`listOf(it)`, not the whole batch), which is
 * what makes the ack per-entry: a throwing handler for one envelope only leaves that envelope
 * unacked, not its siblings.
 *
 * [relay]'s single-flight mutex is what stops an opportunistic [drain] racing a [pump] tick — both
 * call [EnvelopeRelay.pollOnce], so the loser blocks until the winner has fetched, delivered and
 * acked. This is per-process only; cross-process overlap remains [store]'s problem via
 * [InboxStore.fetchPending], exactly as for the outbox.
 *
 * A [store] failure propagates out of [deliver], so the router records a failed destination and the
 * producer's outbox retries — the only remaining way an inboxed context can un-ack an envelope.
 */
class EventInbox
internal constructor(
    private val inner: EventDestination,
    private val store: InboxStore,
    private val pumpScope: CoroutineScope,
    private val tuning: InboxConfig,
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
