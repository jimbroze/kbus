package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.RecordingDestination
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.messages.event.EventRouter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private class OutboxPollerTestEvent(val name: String) : IntegrationEvent()

@OptIn(ExperimentalCoroutinesApi::class)
class OutboxPollerTest {
    @Test
    fun run_fetchesUsingTheConfiguredBatchSize() = runTest {
        val store = RecordingOutboxStore()
        val poller =
            OutboxPoller(store, EventRouter(listOf(RecordingDestination())), 42, 10.milliseconds)

        val job = backgroundScope.launch { poller.run() }
        runCurrent()

        assertEquals(listOf(42), store.fetchLimits)
        job.cancel()
    }

    @Test
    fun run_aThrowingStore_doesNotKillTheLoop() = runTest {
        val store = RecordingOutboxStore()
        store.fetchFailure = IllegalStateException("db down")
        val poller =
            OutboxPoller(store, EventRouter(listOf(RecordingDestination())), 10, 10.milliseconds)

        val job = backgroundScope.launch { poller.run() }
        runCurrent()
        assertEquals(1, store.fetchLimits.size)

        advanceTimeBy(10.milliseconds)
        runCurrent()
        assertEquals(2, store.fetchLimits.size)

        job.cancel()
    }

    @Test
    fun run_deliversFetchedEntriesAndMarksThemPublished() = runTest {
        val store = RecordingOutboxStore()
        store.save(listOf(EventEnvelope("entry-1", OutboxPollerTestEvent("a"))))
        val destination = RecordingDestination()
        val poller = OutboxPoller(store, EventRouter(listOf(destination)), 10, 10.milliseconds)

        val job = backgroundScope.launch { poller.run() }
        runCurrent()

        assertEquals(listOf("entry-1"), store.markedPublished)
        job.cancel()
    }
}
