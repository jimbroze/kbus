package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.api.inbox.InboxStore
import com.jimbroze.kbus.api.messages.event.EventEnvelope

/**
 * Mirrors [RecordingOutboxStore] but implements **real** id-dedupe (the union of pending and
 * consumed ids), so inbox tests exercise the [InboxStore] contract rather than a laxer double.
 */
class RecordingInboxStore : InboxStore {
    val saved = mutableListOf<EventEnvelope>()
    val markedConsumed = mutableListOf<String>()
    val fetchLimits = mutableListOf<Int>()
    var saveFailure: Throwable? = null
    var fetchFailure: Throwable? = null

    private val dedupedIds = mutableSetOf<String>()

    override suspend fun save(envelopes: List<EventEnvelope>) {
        saveFailure?.let { throw it }
        envelopes.forEach { if (dedupedIds.add(it.id)) saved.add(it) }
    }

    override suspend fun fetchPending(limit: Int): List<EventEnvelope> {
        fetchLimits.add(limit)
        fetchFailure?.let { throw it }
        val consumedIds = markedConsumed.toSet()
        return saved.filterNot { it.id in consumedIds }.take(limit)
    }

    override suspend fun markConsumed(ids: List<String>) {
        markedConsumed.addAll(ids)
    }
}
