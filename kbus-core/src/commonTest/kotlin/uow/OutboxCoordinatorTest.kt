package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.api.messages.event.EventEnvelope
import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.RecordingDestination
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.messages.event.routing.EventRouter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private class OutboxCoordinatorTestEvent(val name: String) : IntegrationEvent()

@OptIn(ExperimentalCoroutinesApi::class)
class OutboxCoordinatorTest {
    @Test
    fun `launches nothing when no outbox is configured`() = runTest {
        val coordinator =
            OutboxCoordinator(null, EventRouter(listOf(RecordingDestination())), backgroundScope)

        coordinator.startPolling()

        assertTrue(backgroundScope.coroutineContext[Job]!!.children.toList().isEmpty())
    }

    @Test
    fun `delivers entries left unpublished when it starts`() = runTest {
        val store = RecordingOutboxStore()
        store.save(
            listOf(EventEnvelope("seeded-1", OutboxCoordinatorTestEvent("from-before-crash")))
        )
        val destination = RecordingDestination()
        val coordinator =
            OutboxCoordinator(
                OutboxConfig(store, pollInterval = 10.milliseconds),
                EventRouter(listOf(destination)),
                backgroundScope,
            )

        coordinator.startPolling()
        advanceTimeBy(1.milliseconds)
        runCurrent()

        assertEquals(listOf("seeded-1"), store.markedPublished)
    }

    @Test
    fun `runs one poller however many times it is started`() = runTest {
        val store = RecordingOutboxStore()
        val coordinator =
            OutboxCoordinator(
                OutboxConfig(store, pollInterval = 10.milliseconds),
                EventRouter(listOf(RecordingDestination())),
                backgroundScope,
            )

        coordinator.startPolling()
        coordinator.startPolling()
        advanceTimeBy(1.milliseconds)
        runCurrent()

        assertEquals(1, store.fetchLimits.size)
    }

    @Test
    fun `keeps polling on the interval it was configured with`() = runTest {
        val store = RecordingOutboxStore()
        val coordinator =
            OutboxCoordinator(
                OutboxConfig(store, pollInterval = 10.milliseconds),
                EventRouter(listOf(RecordingDestination())),
                backgroundScope,
            )

        coordinator.startPolling()
        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertEquals(1, store.fetchLimits.size)

        advanceTimeBy(10.milliseconds)
        runCurrent()
        assertEquals(2, store.fetchLimits.size)

        advanceTimeBy(10.milliseconds)
        runCurrent()
        assertEquals(3, store.fetchLimits.size)
    }
}
