package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.RecordingDestination
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.messages.event.routing.EventRouter
import com.jimbroze.kbus.infrastructure.event.EventDestination
import com.jimbroze.kbus.infrastructure.event.EventEnvelope
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
    fun `saves published entries to its store immediately`() = runTest {
        val store = RecordingOutboxStore()
        val publisher =
            ImmediateOutboxPublisher(store, EventRouter(listOf(RecordingDestination())), this)

        publisher.publish(listOf(ImmediateOutboxTestEvent("a"), ImmediateOutboxTestEvent("b")))

        assertEquals(2, store.saved.size)
    }

    @Test
    fun `delivers entries through the router and marks them published`() = runTest {
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
    fun `leaves an entry unpublished for the poller when its delivery fails`() = runTest {
        val store = RecordingOutboxStore()
        val flakyDestination =
            object : EventDestination {
                override val name = "flaky"

                override fun appliesTo(event: IntegrationEvent) = true

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
    fun `saves without delivering when opportunistic draining is off`() = runTest {
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
    fun `propagates a store failure to the caller`() = runTest {
        val store = RecordingOutboxStore().apply { saveFailure = IllegalStateException("db down") }
        val publisher =
            ImmediateOutboxPublisher(store, EventRouter(listOf(RecordingDestination())), this)

        assertFailsWith<IllegalStateException> {
            publisher.publish(listOf(ImmediateOutboxTestEvent("a")))
        }
    }
}
