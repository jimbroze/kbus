package com.jimbroze.kbus.core.boundedcontext.inbox

import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.RecordingDestination
import com.jimbroze.kbus.core.fixtures.RecordingInboxStore
import com.jimbroze.kbus.infrastructure.event.EventEnvelope
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
    fun `takes its name and the events it accepts from the destination it wraps`() = runTest {
        val destination = RecordingDestination(name = "orders")
        val inbox =
            EventInbox(
                destination,
                RecordingInboxStore(),
                backgroundScope,
                InboxTuning(10.milliseconds, 10),
            )

        assertEquals("orders", inbox.name)
        assertTrue(inbox.appliesTo(InboxTestEvent("a")))
    }

    @Test
    fun `saves a delivered envelope to its store without dispatching it`() = runTest {
        val store = RecordingInboxStore()
        val destination = RecordingDestination()
        val inbox =
            EventInbox(
                destination,
                store,
                backgroundScope,
                InboxTuning(10.milliseconds, 10, opportunisticDispatch = false),
            )

        inbox.deliver(listOf(EventEnvelope("1", InboxTestEvent("a"))))
        runCurrent()

        assertEquals(1, store.saved.size)
        assertTrue(destination.delivered.isEmpty())
    }

    @Test
    fun `saves nothing for an empty delivery`() = runTest {
        val store = RecordingInboxStore()
        val inbox =
            EventInbox(
                RecordingDestination(),
                store,
                backgroundScope,
                InboxTuning(10.milliseconds, 10),
            )

        inbox.deliver(emptyList())

        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun `propagates a store failure so the entry is left unacknowledged`() = runTest {
        val store = RecordingInboxStore().apply { saveFailure = IllegalStateException("db down") }
        val inbox =
            EventInbox(
                RecordingDestination(),
                store,
                backgroundScope,
                InboxTuning(10.milliseconds, 10),
            )

        assertFailsWith<IllegalStateException> {
            inbox.deliver(listOf(EventEnvelope("1", InboxTestEvent("a"))))
        }
    }

    @Test
    fun `dispatches to the wrapped destination when opportunistic dispatch is on`() = runTest {
        val store = RecordingInboxStore()
        val destination = RecordingDestination()
        val inbox =
            EventInbox(destination, store, backgroundScope, InboxTuning(10.milliseconds, 10))

        inbox.deliver(listOf(EventEnvelope("1", InboxTestEvent("a"))))
        runCurrent()

        assertEquals(listOf("1"), destination.delivered.map { it.id })
        assertEquals(listOf("1"), store.markedConsumed)
    }

    @Test
    fun `dispatches each drained envelope in its own delivery`() = runTest {
        val store = RecordingInboxStore()
        val destination = RecordingDestination()
        val inbox =
            EventInbox(
                destination,
                store,
                backgroundScope,
                InboxTuning(10.milliseconds, 10, opportunisticDispatch = false),
            )
        store.save(
            listOf(EventEnvelope("1", InboxTestEvent("a")), EventEnvelope("2", InboxTestEvent("b")))
        )

        inbox.drain()

        assertEquals(2, destination.deliveredCalls.size)
        assertTrue(destination.deliveredCalls.all { it.size == 1 })
    }

    @Test
    fun `leaves an envelope pending and unconsumed when its delivery fails`() = runTest {
        val store = RecordingInboxStore()
        val destination = RecordingDestination()
        destination.failure = IllegalStateException("handler failed")
        val inbox =
            EventInbox(
                destination,
                store,
                backgroundScope,
                InboxTuning(10.milliseconds, 10, opportunisticDispatch = false),
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
    fun `drains the remaining envelopes when one of them fails`() = runTest {
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
                InboxTuning(10.milliseconds, 10, opportunisticDispatch = false),
            )
        store.save(
            listOf(EventEnvelope("1", InboxTestEvent("a")), EventEnvelope("2", InboxTestEvent("b")))
        )

        inbox.drain()

        assertEquals(listOf("2"), store.markedConsumed)
        assertEquals(listOf("1"), store.fetchPending(10).map { it.id })
    }

    @Test
    fun `dispatches nothing for an envelope whose id was already consumed`() = runTest {
        val store = RecordingInboxStore()
        val destination = RecordingDestination()
        val inbox =
            EventInbox(
                destination,
                store,
                backgroundScope,
                InboxTuning(10.milliseconds, 10, opportunisticDispatch = false),
            )
        store.save(listOf(EventEnvelope("1", InboxTestEvent("a"))))
        inbox.drain()
        assertEquals(1, destination.deliveredCalls.size)

        inbox.deliver(listOf(EventEnvelope("1", InboxTestEvent("redelivered"))))
        inbox.drain()

        assertEquals(1, destination.deliveredCalls.size)
    }

    @Test
    fun `queues an envelope once when its id is already pending`() = runTest {
        val store = RecordingInboxStore()
        val destination = RecordingDestination()
        val inbox =
            EventInbox(
                destination,
                store,
                backgroundScope,
                InboxTuning(10.milliseconds, 10, opportunisticDispatch = false),
            )

        inbox.deliver(listOf(EventEnvelope("1", InboxTestEvent("a"))))
        inbox.deliver(listOf(EventEnvelope("1", InboxTestEvent("a-again"))))
        inbox.drain()

        assertEquals(1, destination.deliveredCalls.size)
    }

    @Test
    fun `makes a concurrent drain wait for the one already running`() = runTest {
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
                InboxTuning(1.hours, 10, opportunisticDispatch = false),
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
    fun `keeps draining on the interval it was configured with`() = runTest {
        val store = RecordingInboxStore()
        val destination = RecordingDestination()
        val inbox =
            EventInbox(
                destination,
                store,
                backgroundScope,
                InboxTuning(10.milliseconds, 10, opportunisticDispatch = false),
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
