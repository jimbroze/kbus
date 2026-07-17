package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.RecordingIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.messages.event.IntegrationEventPublisher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

private class OutboxTestEvent(val name: String) : IntegrationEvent()

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionOutboxTest {
    @Test
    fun publish_savesEntriesToTheStoreWithUniqueIds() = runTest {
        val store = RecordingOutboxStore()
        val outbox = TransactionOutbox(store, RecordingIntegrationEventPublisher(), this, true)

        outbox.publish(listOf(OutboxTestEvent("a"), OutboxTestEvent("b")))

        assertEquals(2, store.saved.size)
        assertEquals(2, store.saved.map { it.id }.toSet().size)
    }

    @Test
    fun publish_doesNotTouchTheRealPublisher() = runTest {
        val store = RecordingOutboxStore()
        val realPublisher = RecordingIntegrationEventPublisher()
        val outbox = TransactionOutbox(store, realPublisher, this, true)

        outbox.publish(listOf(OutboxTestEvent("a")))

        assertTrue(realPublisher.publishedEvents.isEmpty())
    }

    @Test
    fun publish_propagatesStoreSaveFailures() = runTest {
        val store = RecordingOutboxStore().apply { saveFailure = IllegalStateException("db down") }
        val outbox = TransactionOutbox(store, RecordingIntegrationEventPublisher(), this, true)

        assertFailsWith<IllegalStateException> { outbox.publish(listOf(OutboxTestEvent("a"))) }
    }

    @Test
    fun publish_isSafeUnderConcurrentCalls() = runTest {
        val store = RecordingOutboxStore()
        val outbox = TransactionOutbox(store, RecordingIntegrationEventPublisher(), this, true)

        coroutineScope {
            (1..10)
                .map { i -> async { outbox.publish(listOf(OutboxTestEvent("event-$i"))) } }
                .awaitAll()
        }

        assertEquals(10, store.saved.size)
        assertEquals(10, store.saved.map { it.id }.toSet().size)
    }

    @Test
    fun drain_publishesBufferedEntriesInOrderViaTheRealPublisher() = runTest {
        val store = RecordingOutboxStore()
        val realPublisher = RecordingIntegrationEventPublisher()
        val outbox = TransactionOutbox(store, realPublisher, this, true)
        outbox.publish(listOf(OutboxTestEvent("a")))
        outbox.publish(listOf(OutboxTestEvent("b")))

        outbox.drain()
        advanceUntilIdle()

        val publishedNames =
            realPublisher.publishedEvents.flatten().map { (it as OutboxTestEvent).name }
        assertEquals(listOf("a", "b"), publishedNames)
    }

    @Test
    fun drain_marksPublishedEntriesInTheStore() = runTest {
        val store = RecordingOutboxStore()
        val realPublisher = RecordingIntegrationEventPublisher()
        val outbox = TransactionOutbox(store, realPublisher, this, true)
        outbox.publish(listOf(OutboxTestEvent("a")))
        val entryId = store.saved.single().id

        outbox.drain()
        advanceUntilIdle()

        assertEquals(listOf(entryId), store.markedPublished)
    }

    @Test
    fun drain_doesNotStopOnASingleEntryFailureAndDoesNotThrow() = runTest {
        val store = RecordingOutboxStore()
        var callCount = 0
        val flakyPublisher =
            object : IntegrationEventPublisher {
                val published = mutableListOf<IntegrationEvent>()

                override suspend fun publish(events: List<IntegrationEvent>) {
                    events.forEach { event ->
                        callCount++
                        if (callCount == 1) error("delivery failed")
                        published.add(event)
                    }
                }
            }
        val outbox = TransactionOutbox(store, flakyPublisher, this, true)
        outbox.publish(listOf(OutboxTestEvent("a")))
        outbox.publish(listOf(OutboxTestEvent("b")))

        outbox.drain()
        advanceUntilIdle()

        assertEquals(listOf("b"), flakyPublisher.published.map { (it as OutboxTestEvent).name })
    }

    @Test
    fun drain_clearsTheBufferAfterDraining() = runTest {
        val store = RecordingOutboxStore()
        val realPublisher = RecordingIntegrationEventPublisher()
        val outbox = TransactionOutbox(store, realPublisher, this, true)
        outbox.publish(listOf(OutboxTestEvent("a")))

        outbox.drain()
        advanceUntilIdle()
        outbox.drain()
        advanceUntilIdle()

        assertEquals(1, realPublisher.publishedEvents.flatten().size)
    }

    @Test
    fun drain_isANoOpWhenDrainAfterCommitIsFalse() = runTest {
        val store = RecordingOutboxStore()
        val realPublisher = RecordingIntegrationEventPublisher()
        val outbox = TransactionOutbox(store, realPublisher, this, false)
        outbox.publish(listOf(OutboxTestEvent("a")))

        outbox.drain()
        advanceUntilIdle()

        assertTrue(realPublisher.publishedEvents.isEmpty())
    }
}
