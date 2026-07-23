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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

private class ImmediateOutboxTestEvent(val name: String) : IntegrationEvent()

@OptIn(ExperimentalCoroutinesApi::class)
class ImmediateOutboxPublisherTest {
    @Test
    fun publish_savesEntriesToTheStoreImmediately() = runTest {
        val store = RecordingOutboxStore()
        val publisher = ImmediateOutboxPublisher(store, RecordingIntegrationEventPublisher(), this)

        publisher.publish(listOf(ImmediateOutboxTestEvent("a"), ImmediateOutboxTestEvent("b")))

        assertEquals(2, store.saved.size)
    }

    @Test
    fun publish_deliversToTheRealPublisherAndMarksEntriesPublished() = runTest {
        val store = RecordingOutboxStore()
        val realPublisher = RecordingIntegrationEventPublisher()
        val publisher = ImmediateOutboxPublisher(store, realPublisher, this)

        publisher.publish(listOf(ImmediateOutboxTestEvent("a")))
        advanceUntilIdle()

        assertEquals(
            listOf("a"),
            realPublisher.publishedEvents.flatten().map { (it as ImmediateOutboxTestEvent).name },
        )
        assertEquals(store.saved.map { it.id }, store.markedPublished)
    }

    @Test
    fun aDeliveryFailure_leavesTheEntryUnpublishedForThePoller() = runTest {
        val store = RecordingOutboxStore()
        val flakyPublisher =
            object : IntegrationEventPublisher {
                override suspend fun publish(events: List<IntegrationEvent>) {
                    error("delivery failed")
                }
            }
        val publisher = ImmediateOutboxPublisher(store, flakyPublisher, this)

        publisher.publish(listOf(ImmediateOutboxTestEvent("a")))
        advanceUntilIdle()

        assertEquals(1, store.saved.size)
        assertTrue(store.markedPublished.isEmpty())
    }

    @Test
    fun opportunisticDrain_false_savesButDoesNotDeliver() = runTest {
        val store = RecordingOutboxStore()
        val realPublisher = RecordingIntegrationEventPublisher()
        val publisher =
            ImmediateOutboxPublisher(store, realPublisher, this, opportunisticDrain = false)

        publisher.publish(listOf(ImmediateOutboxTestEvent("a")))
        advanceUntilIdle()

        assertEquals(1, store.saved.size)
        assertTrue(realPublisher.publishedEvents.isEmpty())
        assertTrue(store.markedPublished.isEmpty())
    }

    @Test
    fun aStoreFailure_propagatesToTheCaller() = runTest {
        val store = RecordingOutboxStore().apply { saveFailure = IllegalStateException("db down") }
        val publisher = ImmediateOutboxPublisher(store, RecordingIntegrationEventPublisher(), this)

        assertFailsWith<IllegalStateException> {
            publisher.publish(listOf(ImmediateOutboxTestEvent("a")))
        }
    }
}
