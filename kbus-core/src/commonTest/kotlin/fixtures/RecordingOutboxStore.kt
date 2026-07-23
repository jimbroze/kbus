package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.contracts.outbox.OutboxEntry
import com.jimbroze.kbus.contracts.outbox.OutboxStore

class RecordingOutboxStore : OutboxStore {
    val saved = mutableListOf<OutboxEntry>()
    val markedPublished = mutableListOf<String>()
    val fetchLimits = mutableListOf<Int>()
    var saveFailure: Throwable? = null
    var fetchFailure: Throwable? = null

    override suspend fun save(entries: List<OutboxEntry>) {
        saveFailure?.let { throw it }
        saved.addAll(entries)
    }

    override suspend fun fetchUnpublished(limit: Int): List<OutboxEntry> {
        fetchLimits.add(limit)
        fetchFailure?.let { throw it }
        val publishedIds = markedPublished.toSet()
        return saved.filterNot { it.id in publishedIds }.take(limit)
    }

    override suspend fun markPublished(ids: List<String>) {
        markedPublished.addAll(ids)
    }
}
