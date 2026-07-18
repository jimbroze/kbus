package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.outbox.OutboxEntry
import com.jimbroze.kbus.contracts.outbox.OutboxStore
import com.jimbroze.kbus.core.messages.event.IntegrationEventPublisher
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Opt-in configuration for the transactional outbox. A peer of
 * [TransactionManager][com.jimbroze.kbus.contracts.uow.TransactionManager] on the bus constructor,
 * not middleware.
 */
class OutboxConfig(
    val store: OutboxStore,
    val pollInterval: Duration = 30.seconds,
    val batchSize: Int = 100,
    val drainAfterCommit: Boolean = true,
)

/**
 * Captures integration events published during a command into [store] (inside the command's
 * transaction), then, after commit, drains the buffered entries to [realPublisher] on [drainScope]
 * as a fire-and-forget background task. The [drain] is a latency optimisation only — a bus-owned
 * poller is the at-least-once delivery guarantee; failed or skipped drains are picked up there.
 */
@OptIn(ExperimentalUuidApi::class)
internal class TransactionOutbox(
    private val store: OutboxStore,
    private val realPublisher: IntegrationEventPublisher,
    private val drainScope: CoroutineScope,
    private val drainAfterCommit: Boolean,
) : IntegrationEventPublisher {
    private val mutex = Mutex()
    private val buffer = mutableListOf<OutboxEntry>()

    override suspend fun publish(events: List<IntegrationEvent>) {
        val entries = events.map { OutboxEntry(Uuid.random().toString(), it) }

        store.save(entries)

        mutex.withLock { buffer.addAll(entries) }
    }

    fun drain() {
        if (!drainAfterCommit) return

        drainScope.launch {
            val entries = mutex.withLock { buffer.toList().also { buffer.clear() } }

            val publishedIds = mutableListOf<String>()
            for (entry in entries) {
                @Suppress("TooGenericExceptionCaught", "SwallowedException")
                try {
                    realPublisher.publish(listOf(entry.event))
                    publishedIds.add(entry.id)
                } catch (e: Exception) {
                    // Left unpublished; the outbox poller will retry it.
                }
            }

            if (publishedIds.isNotEmpty()) store.markPublished(publishedIds)
        }
    }
}

/**
 * The transactional outbox's at-least-once delivery guarantee. Runs forever once started: polls
 * [store] for unpublished entries, delivers them via [publisher], and marks the successes. Never
 * throws — a failing batch is simply retried on the next tick.
 */
internal class OutboxPoller(
    private val store: OutboxStore,
    private val publisher: IntegrationEventPublisher,
    private val batchSize: Int,
    private val pollInterval: Duration,
) {
    suspend fun run() {
        while (true) {
            pollOnce()
            delay(pollInterval)
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun pollOnce() {
        try {
            val entries = store.fetchUnpublished(batchSize)

            val publishedIds = mutableListOf<String>()
            for (entry in entries) {
                try {
                    publisher.publish(listOf(entry.event))
                    publishedIds.add(entry.id)
                } catch (e: Exception) {
                    // Left unpublished; retried on the next poll.
                }
            }

            if (publishedIds.isNotEmpty()) store.markPublished(publishedIds)
        } catch (e: Exception) {
            // Never crash the polling loop; retried on the next poll.
        }
    }
}
