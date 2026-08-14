package com.jimbroze.kbus.core.infrastructure.inbox

import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import com.jimbroze.kbus.infrastructure.event.EventEnvelope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private class InboxTestEvent(val name: String) : IntegrationEvent()

class InMemoryInboxStoreTest {
    @Test
    fun `returns pending envelopes oldest first`() = runTest {
        val store = InMemoryInboxStore()
        val first = EventEnvelope("1", InboxTestEvent("first"))
        val second = EventEnvelope("2", InboxTestEvent("second"))

        store.save(listOf(first))
        store.save(listOf(second))

        val fetched = store.fetchPending(10)

        assertEquals(listOf("1", "2"), fetched.map { it.id })
    }

    @Test
    fun `returns no more envelopes than the limit it was asked for`() = runTest {
        val store = InMemoryInboxStore()
        store.save((1..5).map { EventEnvelope("$it", InboxTestEvent("event-$it")) })

        val fetched = store.fetchPending(2)

        assertEquals(listOf("1", "2"), fetched.map { it.id })
    }

    @Test
    fun `ignores an envelope whose id is already pending`() = runTest {
        val store = InMemoryInboxStore()
        store.save(listOf(EventEnvelope("1", InboxTestEvent("first"))))

        store.save(listOf(EventEnvelope("1", InboxTestEvent("duplicate"))))

        assertEquals(1, store.fetchPending(10).size)
    }

    @Test
    fun `ignores an envelope whose id was already consumed`() = runTest {
        val store = InMemoryInboxStore()
        store.save(listOf(EventEnvelope("1", InboxTestEvent("first"))))
        store.markConsumed(listOf("1"))

        store.save(listOf(EventEnvelope("1", InboxTestEvent("redelivered"))))

        assertTrue(store.fetchPending(10).isEmpty())
    }

    @Test
    fun `keeps the new envelopes of a batch and drops the duplicates`() = runTest {
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
    fun `never returns an envelope again once it is marked consumed`() = runTest {
        val store = InMemoryInboxStore()
        store.save(listOf(EventEnvelope("1", InboxTestEvent("first"))))
        store.save(listOf(EventEnvelope("2", InboxTestEvent("second"))))

        store.markConsumed(listOf("1"))

        assertEquals(listOf("2"), store.fetchPending(10).map { it.id })
    }

    @Test
    fun `ignores an instruction to consume an id it does not hold`() = runTest {
        val store = InMemoryInboxStore()
        store.save(listOf(EventEnvelope("1", InboxTestEvent("first"))))

        store.markConsumed(listOf("unknown-id"))

        assertTrue(store.fetchPending(10).map { it.id }.contains("1"))
    }
}
