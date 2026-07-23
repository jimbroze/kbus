package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.contracts.messages.event.EventDestination
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.RecordingDestination
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.messages.event.EventRouter
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
        val publisher =
            ImmediateOutboxPublisher(store, EventRouter(listOf(RecordingDestination())), this)

        publisher.publish(listOf(ImmediateOutboxTestEvent("a"), ImmediateOutboxTestEvent("b")))

        assertEquals(2, store.saved.size)
    }

    @Test
    fun publish_deliversToTheRouterAndMarksEntriesPublished() = runTest {
        val store = RecordingOutboxStore()
        val destination = RecordingDestination()
        val publisher = ImmediateOutboxPublisher(store, EventRouter(listOf(destination)), this)

        publisher.publish(listOf(ImmediateOutboxTestEvent("a")))
        advanceUntilIdle()

        assertEquals(
            listOf("a"),
            destination.delivered.map { (it.event as ImmediateOutboxTestEvent).name },
        )
        assertEquals(store.saved.map { it.id }, store.markedPublished)
    }

    @Test
    fun aDeliveryFailure_leavesTheEntryUnpublishedForThePoller() = runTest {
        val store = RecordingOutboxStore()
        val flakyDestination =
            object : EventDestination {
                override val name = "flaky"

                override fun accepts(event: IntegrationEvent) = true

                override suspend fun deliver(envelopes: List<EventEnvelope>) {
                    error("delivery failed")
                }
            }
        val publisher = ImmediateOutboxPublisher(store, EventRouter(listOf(flakyDestination)), this)

        publisher.publish(listOf(ImmediateOutboxTestEvent("a")))
        advanceUntilIdle()

        assertEquals(1, store.saved.size)
        assertTrue(store.markedPublished.isEmpty())
    }

    @Test
    fun opportunisticDrain_false_savesButDoesNotDeliver() = runTest {
        val store = RecordingOutboxStore()
        val destination = RecordingDestination()
        val publisher =
            ImmediateOutboxPublisher(
                store,
                EventRouter(listOf(destination)),
                this,
                opportunisticDrain = false,
            )

        publisher.publish(listOf(ImmediateOutboxTestEvent("a")))
        advanceUntilIdle()

        assertEquals(1, store.saved.size)
        assertTrue(destination.delivered.isEmpty())
        assertTrue(store.markedPublished.isEmpty())
    }

    @Test
    fun aStoreFailure_propagatesToTheCaller() = runTest {
        val store = RecordingOutboxStore().apply { saveFailure = IllegalStateException("db down") }
        val publisher =
            ImmediateOutboxPublisher(store, EventRouter(listOf(RecordingDestination())), this)

        assertFailsWith<IllegalStateException> {
            publisher.publish(listOf(ImmediateOutboxTestEvent("a")))
        }
    }
}
