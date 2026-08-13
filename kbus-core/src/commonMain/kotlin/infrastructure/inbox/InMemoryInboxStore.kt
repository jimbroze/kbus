package com.jimbroze.kbus.core.infrastructure.inbox

import com.jimbroze.kbus.api.inbox.InboxStore
import com.jimbroze.kbus.api.messages.event.EventEnvelope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reference, non-durable [InboxStore] backed by an in-memory list. For tests and examples.
 *
 * [dedupedIds] is the tombstone set — the union of pending and consumed ids — and is never pruned
 * here; a real store must prune it by time, per [InboxStore.markConsumed]'s retention contract.
 */
class InMemoryInboxStore : InboxStore {
    private val mutex = Mutex()
    private val pending = mutableListOf<EventEnvelope>()
    private val dedupedIds = mutableSetOf<String>()

    override suspend fun save(envelopes: List<EventEnvelope>) {
        mutex.withLock { envelopes.forEach { if (dedupedIds.add(it.id)) pending.add(it) } }
    }

    override suspend fun fetchPending(limit: Int): List<EventEnvelope> {
        mutex.withLock {
            return pending.take(limit)
        }
    }

    override suspend fun markConsumed(ids: List<String>) {
        mutex.withLock { pending.removeAll { it.id in ids } }
    }
}
