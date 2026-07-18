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
    fun publish_doesNotTouchTheStoreBeforeFlush() = runTest {
        val store = RecordingOutboxStore()
        val outbox = TransactionOutbox(store, RecordingIntegrationEventPublisher(), this)

        outbox.publish(listOf(OutboxTestEvent("a"), OutboxTestEvent("b")))

        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun publish_doesNotTouchTheRealPublisher() = runTest {
        val store = RecordingOutboxStore()
        val realPublisher = RecordingIntegrationEventPublisher()
        val outbox = TransactionOutbox(store, realPublisher, this)

        outbox.publish(listOf(OutboxTestEvent("a")))

        assertTrue(realPublisher.publishedEvents.isEmpty())
    }

    @Test
    fun flush_savesEntriesToTheStoreWithUniqueIds() = runTest {
        val store = RecordingOutboxStore()
        val outbox = TransactionOutbox(store, RecordingIntegrationEventPublisher(), this)
        outbox.publish(listOf(OutboxTestEvent("a"), OutboxTestEvent("b")))

        outbox.flush()

        assertEquals(2, store.saved.size)
        assertEquals(2, store.saved.map { it.id }.toSet().size)
    }

    @Test
    fun flush_isANoOpWhenNothingWasPublished() = runTest {
        val store = RecordingOutboxStore()
        val outbox = TransactionOutbox(store, RecordingIntegrationEventPublisher(), this)

        outbox.flush()

        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun flush_propagatesStoreSaveFailures() = runTest {
        val store = RecordingOutboxStore().apply { saveFailure = IllegalStateException("db down") }
        val outbox = TransactionOutbox(store, RecordingIntegrationEventPublisher(), this)
        outbox.publish(listOf(OutboxTestEvent("a")))

        assertFailsWith<IllegalStateException> { outbox.flush() }
    }

    @Test
    fun publish_afterFlush_savesToTheStoreImmediately() = runTest {
        val store = RecordingOutboxStore()
        val outbox = TransactionOutbox(store, RecordingIntegrationEventPublisher(), this)
        outbox.flush()

        outbox.publish(listOf(OutboxTestEvent("late")))

        assertEquals(1, store.saved.size)
        assertEquals("late", (store.saved.single().event as OutboxTestEvent).name)
    }

    @Test
    fun publish_beforeFlush_isNotSavedTwiceByASubsequentFlush() = runTest {
        val store = RecordingOutboxStore()
        val outbox = TransactionOutbox(store, RecordingIntegrationEventPublisher(), this)
        outbox.publish(listOf(OutboxTestEvent("a")))
        outbox.flush()

        outbox.flush()

        assertEquals(1, store.saved.size)
    }

    @Test
    fun publish_isSafeUnderConcurrentCallsBeforeFlush() = runTest {
        val store = RecordingOutboxStore()
        val outbox = TransactionOutbox(store, RecordingIntegrationEventPublisher(), this)

        coroutineScope {
            (1..10)
                .map { i -> async { outbox.publish(listOf(OutboxTestEvent("event-$i"))) } }
                .awaitAll()
        }
        outbox.flush()

        assertEquals(10, store.saved.size)
        assertEquals(10, store.saved.map { it.id }.toSet().size)
    }

    @Test
    fun drain_publishesBufferedEntriesInOrderViaTheRealPublisher() = runTest {
        val store = RecordingOutboxStore()
        val realPublisher = RecordingIntegrationEventPublisher()
        val outbox = TransactionOutbox(store, realPublisher, this)
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
        val outbox = TransactionOutbox(store, realPublisher, this)
        outbox.publish(listOf(OutboxTestEvent("a")))
        outbox.flush()
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
        val outbox = TransactionOutbox(store, flakyPublisher, this)
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
        val outbox = TransactionOutbox(store, realPublisher, this)
        outbox.publish(listOf(OutboxTestEvent("a")))

        outbox.drain()
        advanceUntilIdle()
        outbox.drain()
        advanceUntilIdle()

        assertEquals(1, realPublisher.publishedEvents.flatten().size)
    }
}
