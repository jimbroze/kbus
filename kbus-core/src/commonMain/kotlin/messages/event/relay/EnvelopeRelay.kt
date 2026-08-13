package com.jimbroze.kbus.core.messages.event.relay

import com.jimbroze.kbus.api.messages.event.EventEnvelope
import com.jimbroze.kbus.api.outbox.OutboxStore
import com.jimbroze.kbus.core.messages.event.routing.EventRouter
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * The one delivery loop shared by the outbox drain, the immediate publisher, the outbox poller, and
 * every per-context inbox pump: fetch a batch, deliver each entry individually, ack the ones that
 * succeeded. A push-only caller can omit [fetch].
 *
 * [maxConcurrentDeliveries] is how many entries of a batch may be in flight at once, and with it
 * the only ordering control this delivery path offers. A value of 1 delivers strictly in the order
 * [fetch] returned, at the cost of letting one slow entry hold up everything behind it; anything
 * higher gives up ordering entirely in exchange for a slow or failing entry not delaying its
 * siblings. Ordering above 1 is not weakened but absent — do not read a low limit as "mostly
 * ordered". Even at 1 the guarantee is per batch and per process: nothing constrains the order of
 * two batches, and a second process polling the same store interleaves with this one freely.
 *
 * [pollOnce] is single-flight: an opportunistic drain and a scheduled pump tick can both call it,
 * and the loser waits for the winner rather than skipping its own turn.
 */
internal class EnvelopeRelay(
    private val fetch: suspend (Int) -> List<EventEnvelope> = { emptyList() },
    private val deliver: suspend (EventEnvelope) -> Unit,
    private val ack: suspend (List<String>) -> Unit,
    private val maxConcurrentDeliveries: Int = 1,
) {
    private val pollMutex = Mutex()

    /**
     * Delivers each entry individually, acking only the ones that did not throw. A failed entry is
     * left unacked for the next poll; its successful siblings are still acked, so one
     * permanently-failing entry cannot block its batch forever.
     */
    suspend fun relay(entries: List<EventEnvelope>) {
        if (entries.isEmpty()) return

        val acked =
            if (maxConcurrentDeliveries <= 1) entries.mapNotNull { deliverForAck(it) }
            else {
                val permits = Semaphore(maxConcurrentDeliveries)
                coroutineScope {
                    entries
                        .map { entry -> async { permits.withPermit { deliverForAck(entry) } } }
                        .awaitAll()
                        .filterNotNull()
                }
            }

        if (acked.isNotEmpty()) ack(acked)
    }

    /** The entry's id if it delivered, null if it failed and should be retried. */
    private suspend fun deliverForAck(entry: EventEnvelope): String? {
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        return try {
            deliver(entry)
            entry.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
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

/** Routes entries individually via [router], marking successes in [store]. */
internal fun outboxRelay(store: OutboxStore, router: EventRouter, maxConcurrentDeliveries: Int) =
    EnvelopeRelay(
        fetch = store::fetchUnpublished,
        deliver = { router.route(listOf(it)) },
        ack = store::markPublished,
        maxConcurrentDeliveries = maxConcurrentDeliveries,
    )
