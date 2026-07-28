package com.jimbroze.kbus.core.module.inbox

import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.RecordingDestination
import com.jimbroze.kbus.core.fixtures.RecordingInboxStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private class InboxTestEvent(val name: String) : IntegrationEvent()

@OptIn(ExperimentalCoroutinesApi::class)
class EventInboxTest {
    @Test
    fun name_and_appliesTo_delegateToTheInnerDestination() = runTest {
        val destination = RecordingDestination(name = "orders")
        val inbox =
            EventInbox(destination, RecordingInboxStore(), backgroundScope, 10, 10.milliseconds)

        assertEquals("orders", inbox.name)
        assertTrue(inbox.appliesTo(InboxTestEvent("a")))
    }

    @Test
    fun deliver_savesToTheStoreWithoutDispatching() = runTest {
        val store = RecordingInboxStore()
        val destination = RecordingDestination()
        val inbox =
            EventInbox(
                destination,
                store,
                backgroundScope,
                10,
                10.milliseconds,
                opportunisticDispatch = false,
            )

        inbox.deliver(listOf(EventEnvelope("1", InboxTestEvent("a"))))
        runCurrent()

        assertEquals(1, store.saved.size)
        assertTrue(destination.delivered.isEmpty())
    }

    @Test
    fun deliver_ofAnEmptyList_doesNothing() = runTest {
        val store = RecordingInboxStore()
        val inbox = EventInbox(RecordingDestination(), store, backgroundScope, 10, 10.milliseconds)

        inbox.deliver(emptyList())

        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun deliver_aStoreFailurePropagates_soTheRouterCanLeaveTheEntryUnacked() = runTest {
        val store = RecordingInboxStore().apply { saveFailure = IllegalStateException("db down") }
        val inbox = EventInbox(RecordingDestination(), store, backgroundScope, 10, 10.milliseconds)

        assertFailsWith<IllegalStateException> {
            inbox.deliver(listOf(EventEnvelope("1", InboxTestEvent("a"))))
        }
    }

    @Test
    fun deliver_withOpportunisticDispatch_dispatchesToTheInnerDestination() = runTest {
        val store = RecordingInboxStore()
        val destination = RecordingDestination()
        val inbox = EventInbox(destination, store, backgroundScope, 10, 10.milliseconds)

        inbox.deliver(listOf(EventEnvelope("1", InboxTestEvent("a"))))
        runCurrent()

        assertEquals(listOf("1"), destination.delivered.map { it.id })
        assertEquals(listOf("1"), store.markedConsumed)
    }

    @Test
    fun drain_dispatchesEachEnvelopeInItsOwnInnerDeliverCall() = runTest {
        val store = RecordingInboxStore()
        val destination = RecordingDestination()
        val inbox =
            EventInbox(
                destination,
                store,
                backgroundScope,
                10,
                10.milliseconds,
                opportunisticDispatch = false,
            )
        store.save(
            listOf(EventEnvelope("1", InboxTestEvent("a")), EventEnvelope("2", InboxTestEvent("b")))
        )

        inbox.drain()

        assertEquals(2, destination.deliveredCalls.size)
        assertTrue(destination.deliveredCalls.all { it.size == 1 })
    }

    @Test
    fun drain_aFailingInnerDelivery_leavesTheEnvelopePendingAndUnconsumed() = runTest {
        val store = RecordingInboxStore()
        val destination = RecordingDestination()
        destination.failure = IllegalStateException("handler failed")
        val inbox =
            EventInbox(
                destination,
                store,
                backgroundScope,
                10,
                10.milliseconds,
                opportunisticDispatch = false,
            )
        store.save(listOf(EventEnvelope("1", InboxTestEvent("a"))))

        inbox.drain()

        assertTrue(store.markedConsumed.isEmpty())
        assertEquals(listOf("1"), store.fetchPending(10).map { it.id })

        destination.failure = null
        inbox.drain()

        assertEquals(listOf("1"), store.markedConsumed)
    }

    @Test
    fun drain_oneFailingEnvelopeDoesNotBlockTheOthers() = runTest {
        val store = RecordingInboxStore()
        val destination = RecordingDestination()
        destination.failureFor = { envelope ->
            if (envelope.id == "1") IllegalStateException("failed") else null
        }
        val inbox =
            EventInbox(
                destination,
                store,
                backgroundScope,
                10,
                10.milliseconds,
                opportunisticDispatch = false,
            )
        store.save(
            listOf(EventEnvelope("1", InboxTestEvent("a")), EventEnvelope("2", InboxTestEvent("b")))
        )

        inbox.drain()

        assertEquals(listOf("2"), store.markedConsumed)
        assertEquals(listOf("1"), store.fetchPending(10).map { it.id })
    }

    @Test
    fun deliver_ofAnAlreadyConsumedEnvelope_doesNotRedispatch() = runTest {
        val store = RecordingInboxStore()
        val destination = RecordingDestination()
        val inbox =
            EventInbox(
                destination,
                store,
                backgroundScope,
                10,
                10.milliseconds,
                opportunisticDispatch = false,
            )
        store.save(listOf(EventEnvelope("1", InboxTestEvent("a"))))
        inbox.drain()
        assertEquals(1, destination.deliveredCalls.size)

        inbox.deliver(listOf(EventEnvelope("1", InboxTestEvent("redelivered"))))
        inbox.drain()

        assertEquals(1, destination.deliveredCalls.size)
    }

    @Test
    fun deliver_ofAnEnvelopeStillPending_doesNotQueueItTwice() = runTest {
        val store = RecordingInboxStore()
        val destination = RecordingDestination()
        val inbox =
            EventInbox(
                destination,
                store,
                backgroundScope,
                10,
                10.milliseconds,
                opportunisticDispatch = false,
            )

        inbox.deliver(listOf(EventEnvelope("1", InboxTestEvent("a"))))
        inbox.deliver(listOf(EventEnvelope("1", InboxTestEvent("a-again"))))
        inbox.drain()

        assertEquals(1, destination.deliveredCalls.size)
    }

    @Test
    fun drain_isSingleFlight_soAnOpportunisticDrainCannotRaceAPumpTick() = runTest {
        val store = RecordingInboxStore()
        val destination = RecordingDestination()
        val order = mutableListOf<String>()
        val gate = Mutex(locked = true)
        var callCount = 0
        destination.beforeDeliver = {
            callCount++
            order.add("deliver-$callCount")
            if (callCount == 1) gate.withLock {}
        }
        val inbox =
            EventInbox(
                destination,
                store,
                backgroundScope,
                10,
                1.hours,
                opportunisticDispatch = false,
            )
        store.save(listOf(EventEnvelope("1", InboxTestEvent("a"))))

        val pumpJob = backgroundScope.launch { inbox.pump() }
        runCurrent()
        val drainJob = launch { inbox.drain() }
        runCurrent()

        assertEquals(
            listOf("deliver-1"),
            order,
            "the explicit drain must wait for the pump's tick to finish",
        )

        gate.unlock()
        drainJob.join()
        pumpJob.cancel()
    }

    @Test
    fun pump_keepsDrainingOnTheConfiguredInterval() = runTest {
        val store = RecordingInboxStore()
        val destination = RecordingDestination()
        val inbox =
            EventInbox(
                destination,
                store,
                backgroundScope,
                10,
                10.milliseconds,
                opportunisticDispatch = false,
            )
        store.save(listOf(EventEnvelope("1", InboxTestEvent("a"))))

        val job = backgroundScope.launch { inbox.pump() }
        runCurrent()
        assertEquals(listOf("1"), destination.delivered.map { it.id })

        store.save(listOf(EventEnvelope("2", InboxTestEvent("b"))))
        advanceTimeBy(10.milliseconds)
        runCurrent()

        assertEquals(listOf("1", "2"), destination.delivered.map { it.id })
        job.cancel()
    }
}
