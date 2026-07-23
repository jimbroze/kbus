package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.outbox.OutboxEntry
import com.jimbroze.kbus.core.fixtures.RecordingIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
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
        val poller = OutboxPoller(store, RecordingIntegrationEventPublisher(), 42, 10.milliseconds)

        val job = backgroundScope.launch { poller.run() }
        runCurrent()

        assertEquals(listOf(42), store.fetchLimits)
        job.cancel()
    }

    @Test
    fun run_aThrowingStore_doesNotKillTheLoop() = runTest {
        val store = RecordingOutboxStore()
        store.fetchFailure = IllegalStateException("db down")
        val poller = OutboxPoller(store, RecordingIntegrationEventPublisher(), 10, 10.milliseconds)

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
        store.save(listOf(OutboxEntry("entry-1", OutboxPollerTestEvent("a"))))
        val realPublisher = RecordingIntegrationEventPublisher()
        val poller = OutboxPoller(store, realPublisher, 10, 10.milliseconds)

        val job = backgroundScope.launch { poller.run() }
        runCurrent()

        assertEquals(listOf("entry-1"), store.markedPublished)
        job.cancel()
    }
}
