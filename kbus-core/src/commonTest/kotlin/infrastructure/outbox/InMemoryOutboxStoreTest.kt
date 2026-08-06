package com.jimbroze.kbus.core.infrastructure.outbox

import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private class OutboxTestEvent(val name: String) : IntegrationEvent()

class InMemoryOutboxStoreTest {
    @Test
    fun `returns unpublished entries oldest first`() = runTest {
        val store = InMemoryOutboxStore()
        val first = EventEnvelope("1", OutboxTestEvent("first"))
        val second = EventEnvelope("2", OutboxTestEvent("second"))

        store.save(listOf(first))
        store.save(listOf(second))

        val fetched = store.fetchUnpublished(10)

        assertEquals(listOf("1", "2"), fetched.map { it.id })
    }

    @Test
    fun `returns no more entries than the limit it was asked for`() = runTest {
        val store = InMemoryOutboxStore()
        store.save((1..5).map { EventEnvelope("$it", OutboxTestEvent("event-$it")) })

        val fetched = store.fetchUnpublished(2)

        assertEquals(listOf("1", "2"), fetched.map { it.id })
    }

    @Test
    fun `never returns an entry again once it is marked published`() = runTest {
        val store = InMemoryOutboxStore()
        store.save(listOf(EventEnvelope("1", OutboxTestEvent("first"))))
        store.save(listOf(EventEnvelope("2", OutboxTestEvent("second"))))

        store.markPublished(listOf("1"))
        val fetched = store.fetchUnpublished(10)

        assertEquals(listOf("2"), fetched.map { it.id })
    }

    @Test
    fun `ignores an instruction to publish an id it does not hold`() = runTest {
        val store = InMemoryOutboxStore()
        store.save(listOf(EventEnvelope("1", OutboxTestEvent("first"))))

        store.markPublished(listOf("unknown-id"))
        val fetched = store.fetchUnpublished(10)

        assertTrue(fetched.map { it.id }.contains("1"))
    }
}
