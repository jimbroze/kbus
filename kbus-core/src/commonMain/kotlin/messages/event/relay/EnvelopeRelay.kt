package com.jimbroze.kbus.core.messages.event.relay

import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.outbox.OutboxStore
import com.jimbroze.kbus.core.messages.event.routing.EventRouter
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one delivery loop shared by the outbox drain, the immediate publisher, the outbox poller, and
 * every per-context inbox pump: fetch a batch, deliver each entry individually, ack the ones that
 * succeeded. [fetch] defaults to an empty batch so the two push-only call sites (drain, immediate
 * publish) construct with just [deliver] and [ack] and need no dummy fetcher.
 *
 * [pollOnce] is single-flight via [pollMutex]: both an inbox's opportunistic drain and its
 * scheduled pump tick call [pollOnce], and serialising them (rather than deduping) is what lets
 * [EventInbox.pump] be a bare [poll] loop instead of a re-implemented one.
 */
internal class EnvelopeRelay(
    private val fetch: suspend (Int) -> List<EventEnvelope> = { emptyList() },
    private val deliver: suspend (EventEnvelope) -> Unit,
    private val ack: suspend (List<String>) -> Unit,
) {
    private val pollMutex = Mutex()

    /** Delivers each entry individually, acking only the ones that did not throw. */
    suspend fun relay(entries: List<EventEnvelope>) {
        val acked = mutableListOf<String>()
        for (entry in entries) {
            @Suppress("TooGenericExceptionCaught", "SwallowedException")
            try {
                deliver(entry)
                acked.add(entry.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Left unacked; the next poll/relay retries it.
            }
        }
        if (acked.isNotEmpty()) ack(acked)
    }

    suspend fun pollOnce(batchSize: Int) =
        pollMutex.withLock {
            @Suppress("TooGenericExceptionCaught", "SwallowedException")
            try {
                relay(fetch(batchSize))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never crash the loop; retried on the next poll.
            }
        }

    suspend fun poll(batchSize: Int, interval: Duration): Nothing {
        while (true) {
            pollOnce(batchSize)
            delay(interval)
        }
    }
}

/** Routes one entry at a time via [router], marking successes in [store]. */
internal fun outboxRelay(store: OutboxStore, router: EventRouter) =
    EnvelopeRelay(
        fetch = store::fetchUnpublished,
        deliver = { router.route(listOf(it)) },
        ack = store::markPublished,
    )
