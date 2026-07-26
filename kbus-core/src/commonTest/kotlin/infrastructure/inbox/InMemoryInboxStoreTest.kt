package com.jimbroze.kbus.core.infrastructure.inbox

import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private class InboxTestEvent(val name: String) : IntegrationEvent()

class InMemoryInboxStoreTest {
    @Test
    fun fetchPending_returnsSavedEnvelopesOldestFirst() = runTest {
        val store = InMemoryInboxStore()
        val first = EventEnvelope("1", InboxTestEvent("first"))
        val second = EventEnvelope("2", InboxTestEvent("second"))

        store.save(listOf(first))
        store.save(listOf(second))

        val fetched = store.fetchPending(10)

        assertEquals(listOf("1", "2"), fetched.map { it.id })
    }

    @Test
    fun fetchPending_respectsLimit() = runTest {
        val store = InMemoryInboxStore()
        store.save((1..5).map { EventEnvelope("$it", InboxTestEvent("event-$it")) })

        val fetched = store.fetchPending(2)

        assertEquals(listOf("1", "2"), fetched.map { it.id })
    }

    @Test
    fun save_ignoresAnEnvelopeWhoseIdIsAlreadyPending() = runTest {
        val store = InMemoryInboxStore()
        store.save(listOf(EventEnvelope("1", InboxTestEvent("first"))))

        store.save(listOf(EventEnvelope("1", InboxTestEvent("duplicate"))))

        assertEquals(1, store.fetchPending(10).size)
    }

    @Test
    fun save_ignoresAnEnvelopeWhoseIdWasAlreadyConsumed() = runTest {
        val store = InMemoryInboxStore()
        store.save(listOf(EventEnvelope("1", InboxTestEvent("first"))))
        store.markConsumed(listOf("1"))

        store.save(listOf(EventEnvelope("1", InboxTestEvent("redelivered"))))

        assertTrue(store.fetchPending(10).isEmpty())
    }

    @Test
    fun save_ofAMixedBatch_keepsTheNewOnesAndDropsTheDuplicates() = runTest {
        val store = InMemoryInboxStore()
        store.save(listOf(EventEnvelope("1", InboxTestEvent("first"))))

        store.save(
            listOf(
                EventEnvelope("1", InboxTestEvent("duplicate")),
                EventEnvelope("2", InboxTestEvent("new")),
            )
        )

        assertEquals(listOf("1", "2"), store.fetchPending(10).map { it.id })
    }

    @Test
    fun markConsumed_excludesEnvelopesFromFutureFetches() = runTest {
        val store = InMemoryInboxStore()
        store.save(listOf(EventEnvelope("1", InboxTestEvent("first"))))
        store.save(listOf(EventEnvelope("2", InboxTestEvent("second"))))

        store.markConsumed(listOf("1"))

        assertEquals(listOf("2"), store.fetchPending(10).map { it.id })
    }

    @Test
    fun markConsumed_toleratesUnknownIds() = runTest {
        val store = InMemoryInboxStore()
        store.save(listOf(EventEnvelope("1", InboxTestEvent("first"))))

        store.markConsumed(listOf("unknown-id"))

        assertTrue(store.fetchPending(10).map { it.id }.contains("1"))
    }
}
