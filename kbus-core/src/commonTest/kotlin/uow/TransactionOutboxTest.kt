package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.RecordingIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
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

/**
 * [TransactionOutbox.flush] and [TransactionOutbox.drain] are private, self-registered into the
 * [UnitOfWork] passed to the constructor — so these tests drive the outbox through a real
 * [DefaultUnitOfWorkFactory]-created unit of work rather than calling them directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionOutboxTest {
    @Test
    fun publish_doesNotTouchTheStoreBeforeCommit() = runTest {
        val store = RecordingOutboxStore()
        val unitOfWork = DefaultUnitOfWorkFactory().create<Unit>()
        val outbox =
            TransactionOutbox(store, RecordingIntegrationEventPublisher(), this, unitOfWork)

        outbox.publish(listOf(OutboxTestEvent("a"), OutboxTestEvent("b")))

        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun publish_doesNotTouchTheRealPublisher() = runTest {
        val store = RecordingOutboxStore()
        val realPublisher = RecordingIntegrationEventPublisher()
        val unitOfWork = DefaultUnitOfWorkFactory().create<Unit>()
        val outbox = TransactionOutbox(store, realPublisher, this, unitOfWork)

        outbox.publish(listOf(OutboxTestEvent("a")))

        assertTrue(realPublisher.publishedEvents.isEmpty())
    }

    @Test
    fun publish_beforeCommit_isSavedToTheStoreWithUniqueIdsDuringTheSecondaryPhase() = runTest {
        val store = RecordingOutboxStore()
        val unitOfWork = DefaultUnitOfWorkFactory().create<Unit>()
        val outbox =
            TransactionOutbox(store, RecordingIntegrationEventPublisher(), this, unitOfWork)
        unitOfWork.setReturningWork {
            outbox.publish(listOf(OutboxTestEvent("a"), OutboxTestEvent("b")))
        }

        unitOfWork.execute()

        assertEquals(2, store.saved.size)
        assertEquals(2, store.saved.map { it.id }.toSet().size)
    }

    @Test
    fun commit_isANoOpFlushWhenNothingWasPublished() = runTest {
        val store = RecordingOutboxStore()
        val unitOfWork = DefaultUnitOfWorkFactory().create<Unit>()
        TransactionOutbox(store, RecordingIntegrationEventPublisher(), this, unitOfWork)
        unitOfWork.setReturningWork {}

        unitOfWork.execute()

        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun commit_propagatesStoreSaveFailures() = runTest {
        val store = RecordingOutboxStore().apply { saveFailure = IllegalStateException("db down") }
        val unitOfWork = DefaultUnitOfWorkFactory().create<Unit>()
        val outbox =
            TransactionOutbox(store, RecordingIntegrationEventPublisher(), this, unitOfWork)
        unitOfWork.setReturningWork { outbox.publish(listOf(OutboxTestEvent("a"))) }

        assertFailsWith<IllegalStateException> { unitOfWork.execute() }
    }

    @Test
    fun publish_afterCommit_savesToTheStoreImmediately() = runTest {
        val store = RecordingOutboxStore()
        val unitOfWork = DefaultUnitOfWorkFactory().create<Unit>()
        val outbox =
            TransactionOutbox(store, RecordingIntegrationEventPublisher(), this, unitOfWork)
        unitOfWork.setReturningWork {}
        unitOfWork.execute()

        outbox.publish(listOf(OutboxTestEvent("late")))

        assertEquals(1, store.saved.size)
        assertEquals("late", (store.saved.single().event as OutboxTestEvent).name)
    }

    @Test
    fun publish_isSafeUnderConcurrentCallsBeforeCommit() = runTest {
        val store = RecordingOutboxStore()
        val unitOfWork = DefaultUnitOfWorkFactory().create<Unit>()
        val outbox =
            TransactionOutbox(store, RecordingIntegrationEventPublisher(), this, unitOfWork)

        coroutineScope {
            (1..10)
                .map { i -> async { outbox.publish(listOf(OutboxTestEvent("event-$i"))) } }
                .awaitAll()
        }
        unitOfWork.setReturningWork {}
        unitOfWork.execute()

        assertEquals(10, store.saved.size)
        assertEquals(10, store.saved.map { it.id }.toSet().size)
    }

    @Test
    fun commit_drainsAndDeliversBufferedEntriesPostCommitViaTheRealPublisher() = runTest {
        val store = RecordingOutboxStore()
        val realPublisher = RecordingIntegrationEventPublisher()
        val unitOfWork = DefaultUnitOfWorkFactory().create<Unit>()
        val outbox = TransactionOutbox(store, realPublisher, this, unitOfWork)
        unitOfWork.setReturningWork {
            outbox.publish(listOf(OutboxTestEvent("a")))
            outbox.publish(listOf(OutboxTestEvent("b")))
        }

        unitOfWork.execute()
        advanceUntilIdle()

        val publishedNames =
            realPublisher.publishedEvents.flatten().map { (it as OutboxTestEvent).name }
        assertEquals(listOf("a", "b"), publishedNames)
    }

    @Test
    fun commit_marksDrainedEntriesAsPublishedInTheStore() = runTest {
        val store = RecordingOutboxStore()
        val realPublisher = RecordingIntegrationEventPublisher()
        val unitOfWork = DefaultUnitOfWorkFactory().create<Unit>()
        val outbox = TransactionOutbox(store, realPublisher, this, unitOfWork)
        unitOfWork.setReturningWork { outbox.publish(listOf(OutboxTestEvent("a"))) }

        unitOfWork.execute()
        advanceUntilIdle()

        assertEquals(store.saved.map { it.id }, store.markedPublished)
    }

    @Test
    fun drainAfterCommit_false_flushesButNeverDrains() = runTest {
        val store = RecordingOutboxStore()
        val realPublisher = RecordingIntegrationEventPublisher()
        val unitOfWork = DefaultUnitOfWorkFactory().create<Unit>()
        val outbox =
            TransactionOutbox(store, realPublisher, this, unitOfWork, drainAfterCommit = false)
        unitOfWork.setReturningWork { outbox.publish(listOf(OutboxTestEvent("a"))) }

        unitOfWork.execute()
        advanceUntilIdle()

        assertEquals(1, store.saved.size, "flush still runs")
        assertTrue(realPublisher.publishedEvents.isEmpty(), "drain is never registered")
        assertTrue(store.markedPublished.isEmpty())
    }

    @Test
    fun aFailingDeliveryDuringDrain_doesNotStopOtherEntriesAndLeavesTheFailureForThePoller() =
        runTest {
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
            val unitOfWork = DefaultUnitOfWorkFactory().create<Unit>()
            val outbox = TransactionOutbox(store, flakyPublisher, this, unitOfWork)
            unitOfWork.setReturningWork {
                outbox.publish(listOf(OutboxTestEvent("a")))
                outbox.publish(listOf(OutboxTestEvent("b")))
            }

            unitOfWork.execute()
            advanceUntilIdle()

            assertEquals(listOf("b"), flakyPublisher.published.map { (it as OutboxTestEvent).name })
            assertEquals(1, store.markedPublished.size, "the failed entry is left for the poller")
        }

    @Test
    fun primaryWorkThrowing_meansNothingIsEverSaved() = runTest {
        val store = RecordingOutboxStore()
        val unitOfWork = DefaultUnitOfWorkFactory().create<Unit>()
        val outbox =
            TransactionOutbox(store, RecordingIntegrationEventPublisher(), this, unitOfWork)
        unitOfWork.setReturningWork {
            outbox.publish(listOf(OutboxTestEvent("a")))
            error("primary work failed")
        }

        assertFailsWith<IllegalStateException> { unitOfWork.execute() }
        advanceUntilIdle()

        assertTrue(store.saved.isEmpty(), "flush never ran; nothing was ever staged")
    }
}
