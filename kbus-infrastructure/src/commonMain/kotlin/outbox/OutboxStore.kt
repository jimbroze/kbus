package com.jimbroze.kbus.infrastructure.outbox

import com.jimbroze.kbus.infrastructure.event.EventEnvelope
import com.jimbroze.kbus.infrastructure.transaction.TransactionManager

/**
 * User-supplied durable storage for the transactional outbox.
 *
 * By convention, like [TransactionManager]: implementations own serialization and durability, and
 * **must join the ambient transaction** — [save] is always called from inside the command's
 * [TransactionManager.execute] block, so a rolled-back transaction must roll back the saved entries
 * too. [fetchUnpublished] and [markPublished] are called outside any transaction, by the
 * post-commit drain and the background poller.
 */
interface OutboxStore {
    /**
     * Called inside the command's [TransactionManager.execute] block. Must join the ambient
     * transaction.
     */
    suspend fun save(entries: List<EventEnvelope>)

    /**
     * Oldest-first, up to [limit] entries. Implementations may hide entries that are in-flight or
     * too fresh, to shrink the window in which the drain and the poller could both deliver the same
     * entry.
     */
    suspend fun fetchUnpublished(limit: Int): List<EventEnvelope>

    /**
     * Marks the given entries as delivered. Implementations may delete them instead of flagging.
     */
    suspend fun markPublished(ids: List<String>)
}
