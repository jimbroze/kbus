package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.contracts.outbox.OutboxEntry
import com.jimbroze.kbus.contracts.outbox.OutboxStore
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
 * Captures integration events published during a command, deferring the store write until [flush]
 * runs. Self-registers into [unitOfWork] at construction time: [flush] as the *first* secondary
 * work item (so it runs inside the command's transaction, ahead of anything registered while
 * handling the command), and, if [drainAfterCommit] is true, [drain] as post-commit work. Publishes
 * that arrive after [flush] has already run (e.g. from SECONDARY/POST_COMMIT-phase handlers) are
 * saved immediately. [drain] delivers the buffered entries to [realPublisher] on [drainScope] as a
 * fire-and-forget background task — a latency optimisation only. A bus-owned poller is the
 * at-least-once delivery guarantee; failed or skipped drains are picked up there.
 */
@OptIn(ExperimentalUuidApi::class)
class TransactionOutbox
internal constructor(
    private val store: OutboxStore,
    private val realPublisher: IntegrationEventPublisher,
    private val drainScope: CoroutineScope,
    unitOfWork: UnitOfWork<*>,
    drainAfterCommit: Boolean = true,
) : IntegrationEventPublisher {
    private val mutex = Mutex()
    private val buffer = mutableListOf<OutboxEntry>()
    private val pendingSave = mutableListOf<OutboxEntry>()
    private var flushed = false

    init {
        unitOfWork.addSecondaryWork { flush() }
        if (drainAfterCommit) unitOfWork.addPostCommitWork { drain() }
    }

    override suspend fun publish(events: List<IntegrationEvent>) {
        val entries = events.map { OutboxEntry(Uuid.random().toString(), it) }

        val alreadyFlushed =
            mutex.withLock {
                buffer.addAll(entries)
                if (!flushed) pendingSave.addAll(entries)
                flushed
            }

        if (alreadyFlushed) store.save(entries)
    }

    /** Saves everything published so far and marks the outbox flushed. Runs as secondary work. */
    private suspend fun flush() {
        val toSave =
            mutex.withLock {
                flushed = true
                pendingSave.toList().also { pendingSave.clear() }
            }

        if (toSave.isNotEmpty()) store.save(toSave)
    }

    private fun drain() {
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
