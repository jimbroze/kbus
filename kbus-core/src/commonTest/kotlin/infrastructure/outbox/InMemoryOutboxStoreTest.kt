package com.jimbroze.kbus.core.infrastructure.outbox

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.outbox.OutboxEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private class OutboxTestEvent(val name: String) : IntegrationEvent()

class InMemoryOutboxStoreTest {
    @Test
    fun fetchUnpublished_returnsSavedEntriesOldestFirst() = runTest {
        val store = InMemoryOutboxStore()
        val first = OutboxEntry("1", OutboxTestEvent("first"))
        val second = OutboxEntry("2", OutboxTestEvent("second"))

        store.save(listOf(first))
        store.save(listOf(second))

        val fetched = store.fetchUnpublished(10)

        assertEquals(listOf("1", "2"), fetched.map { it.id })
    }

    @Test
    fun fetchUnpublished_respectsLimit() = runTest {
        val store = InMemoryOutboxStore()
        store.save((1..5).map { OutboxEntry("$it", OutboxTestEvent("event-$it")) })

        val fetched = store.fetchUnpublished(2)

        assertEquals(listOf("1", "2"), fetched.map { it.id })
    }

    @Test
    fun markPublished_excludesEntriesFromFutureFetches() = runTest {
        val store = InMemoryOutboxStore()
        store.save(listOf(OutboxEntry("1", OutboxTestEvent("first"))))
        store.save(listOf(OutboxEntry("2", OutboxTestEvent("second"))))

        store.markPublished(listOf("1"))
        val fetched = store.fetchUnpublished(10)

        assertEquals(listOf("2"), fetched.map { it.id })
    }

    @Test
    fun markPublished_toleratesUnknownIds() = runTest {
        val store = InMemoryOutboxStore()
        store.save(listOf(OutboxEntry("1", OutboxTestEvent("first"))))

        store.markPublished(listOf("unknown-id"))
        val fetched = store.fetchUnpublished(10)

        assertTrue(fetched.map { it.id }.contains("1"))
    }
}
