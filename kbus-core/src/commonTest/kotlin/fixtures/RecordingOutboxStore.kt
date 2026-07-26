package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.outbox.OutboxStore

class RecordingOutboxStore : OutboxStore {
    val saved = mutableListOf<EventEnvelope>()
    val markedPublished = mutableListOf<String>()
    val fetchLimits = mutableListOf<Int>()
    var saveFailure: Throwable? = null
    var fetchFailure: Throwable? = null

    private val published = mutableSetOf<String>()

    override suspend fun save(entries: List<EventEnvelope>) {
        saveFailure?.let { throw it }
        saved.addAll(entries)
    }

    override suspend fun fetchUnpublished(limit: Int): List<EventEnvelope> {
        fetchLimits.add(limit)
        fetchFailure?.let { throw it }
        return saved.filterNot { it.id in published }.take(limit)
    }

    // Idempotent, like a real store: re-marking an already-published id (e.g. the opportunistic
    // drain and the poller both winning a race for the same envelope) is a no-op, not a second
    // entry.
    override suspend fun markPublished(ids: List<String>) {
        ids.forEach { if (published.add(it)) markedPublished.add(it) }
    }
}
