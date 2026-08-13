package com.jimbroze.kbus.core.infrastructure.outbox

import com.jimbroze.kbus.api.messages.event.EventEnvelope
import com.jimbroze.kbus.api.outbox.OutboxStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reference, non-durable [OutboxStore] backed by an in-memory list. For tests and examples. */
class InMemoryOutboxStore : OutboxStore {
    private val mutex = Mutex()
    private val entries = mutableListOf<EventEnvelope>()

    override suspend fun save(entries: List<EventEnvelope>) {
        mutex.withLock { this.entries.addAll(entries) }
    }

    override suspend fun fetchUnpublished(limit: Int): List<EventEnvelope> {
        mutex.withLock {
            return entries.take(limit)
        }
    }

    override suspend fun markPublished(ids: List<String>) {
        mutex.withLock { entries.removeAll { it.id in ids } }
    }
}
