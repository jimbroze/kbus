package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.outbox.OutboxEntry
import com.jimbroze.kbus.core.fixtures.RecordingIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private class OutboxCoordinatorTestEvent(val name: String) : IntegrationEvent()

@OptIn(ExperimentalCoroutinesApi::class)
class OutboxCoordinatorTest {
    @Test
    fun startPolling_withNoConfig_launchesNothing() = runTest {
        val scope = CoroutineScope(Job())
        val coordinator = OutboxCoordinator(null, RecordingIntegrationEventPublisher(), scope)

        coordinator.startPolling()

        assertTrue(scope.coroutineContext[Job]!!.children.toList().isEmpty())
    }

    @Test
    fun startPolling_deliversPreExistingUnpublishedEntries() = runTest {
        val store = RecordingOutboxStore()
        store.save(listOf(OutboxEntry("seeded-1", OutboxCoordinatorTestEvent("from-before-crash"))))
        val realPublisher = RecordingIntegrationEventPublisher()
        val coordinator =
            OutboxCoordinator(
                OutboxConfig(store, pollInterval = 10.milliseconds),
                realPublisher,
                backgroundScope,
            )

        coordinator.startPolling()
        advanceTimeBy(1.milliseconds)
        runCurrent()

        assertEquals(listOf("seeded-1"), store.markedPublished)
    }

    @Test
    fun startPolling_calledTwice_runsOnlyOnePoller() = runTest {
        val store = RecordingOutboxStore()
        val coordinator =
            OutboxCoordinator(
                OutboxConfig(store, pollInterval = 10.milliseconds),
                RecordingIntegrationEventPublisher(),
                backgroundScope,
            )

        coordinator.startPolling()
        coordinator.startPolling()
        advanceTimeBy(1.milliseconds)
        runCurrent()

        assertEquals(1, store.fetchLimits.size)
    }

    @Test
    fun startPolling_keepsPollingOnTheConfiguredInterval() = runTest {
        val store = RecordingOutboxStore()
        val coordinator =
            OutboxCoordinator(
                OutboxConfig(store, pollInterval = 10.milliseconds),
                RecordingIntegrationEventPublisher(),
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
