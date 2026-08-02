package com.jimbroze.kbus.core.messages.event.relay

import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private class RelayTestEvent(val name: String) : IntegrationEvent()

@OptIn(ExperimentalCoroutinesApi::class)
class EnvelopeRelayTest {
    @Test
    fun relay_deliversEachEntryIndividually() = runTest {
        val delivered = mutableListOf<EventEnvelope>()
        val relay = EnvelopeRelay(deliver = { delivered.add(it) }, ack = {})

        val entries =
            listOf(EventEnvelope("1", RelayTestEvent("a")), EventEnvelope("2", RelayTestEvent("b")))
        relay.relay(entries)

        assertEquals(entries, delivered)
    }

    @Test
    fun relay_deliversUpToMaxConcurrentDeliveriesAtOnce() = runTest {
        val relay =
            EnvelopeRelay(deliver = { delay(10.seconds) }, ack = {}, maxConcurrentDeliveries = 2)

        relay.relay((1..4).map { EventEnvelope("$it", RelayTestEvent("e$it")) })

        // Four entries, two at a time, ten seconds each: two waves, not four.
        assertEquals(20.seconds.inWholeMilliseconds, testScheduler.currentTime)
    }

    @Test
    fun relay_withMaxConcurrentDeliveriesOfOne_deliversInOrder() = runTest {
        val delivered = mutableListOf<String>()
        val relay =
            EnvelopeRelay(
                deliver = {
                    delay((10 - it.id.toInt()).seconds)
                    delivered.add(it.id)
                },
                ack = {},
                maxConcurrentDeliveries = 1,
            )

        relay.relay((1..3).map { EventEnvelope("$it", RelayTestEvent("e$it")) })

        // Descending delays, so any overlap at all would reverse them.
        assertEquals(listOf("1", "2", "3"), delivered)
    }

    @Test
    fun relay_acksOnlyTheEntriesThatDelivered() = runTest {
        val acked = mutableListOf<List<String>>()
        val relay =
            EnvelopeRelay(
                deliver = { if (it.id == "2") error("delivery failed") },
                ack = { acked.add(it) },
            )

        relay.relay(
            listOf(
                EventEnvelope("1", RelayTestEvent("a")),
                EventEnvelope("2", RelayTestEvent("b")),
                EventEnvelope("3", RelayTestEvent("c")),
            )
        )

        assertEquals(listOf(listOf("1", "3")), acked)
    }

    @Test
    fun relay_withNoSuccessfulDeliveries_doesNotCallAck() = runTest {
        var ackCalls = 0
        val relay = EnvelopeRelay(deliver = { error("delivery failed") }, ack = { ackCalls++ })

        relay.relay(listOf(EventEnvelope("1", RelayTestEvent("a"))))

        assertEquals(0, ackCalls)
    }

    @Test
    fun relay_anEmptyBatchDeliversAndAcksNothing() = runTest {
        var deliverCalls = 0
        var ackCalls = 0
        val relay = EnvelopeRelay(deliver = { deliverCalls++ }, ack = { ackCalls++ })

        relay.relay(emptyList())

        assertEquals(0, deliverCalls)
        assertEquals(0, ackCalls)
    }

    @Test
    fun pollOnce_fetchesUsingTheGivenBatchSize() = runTest {
        val fetchLimits = mutableListOf<Int>()
        val relay =
            EnvelopeRelay(
                fetch = {
                    fetchLimits.add(it)
                    emptyList()
                },
                deliver = {},
                ack = {},
            )

        relay.pollOnce(42)

        assertEquals(listOf(42), fetchLimits)
    }

    @Test
    fun pollOnce_aThrowingFetch_isSwallowed() = runTest {
        val relay = EnvelopeRelay(fetch = { error("db down") }, deliver = {}, ack = {})

        relay.pollOnce(10)
    }

    @Test
    fun pollOnce_deliversFetchedEntriesAndAcksThem() = runTest {
        val entry = EventEnvelope("entry-1", RelayTestEvent("a"))
        val delivered = mutableListOf<EventEnvelope>()
        val acked = mutableListOf<String>()
        val relay =
            EnvelopeRelay(
                fetch = { listOf(entry) },
                deliver = { delivered.add(it) },
                ack = { acked.addAll(it) },
            )

        relay.pollOnce(10)

        assertEquals(listOf(entry), delivered)
        assertEquals(listOf("entry-1"), acked)
    }

    @Test
    fun pollOnce_isSingleFlight_aSecondConcurrentCallWaitsForTheFirst() = runTest {
        val order = mutableListOf<String>()
        val gate = Mutex(locked = true)
        var fetchCount = 0
        val relay =
            EnvelopeRelay(
                fetch = {
                    fetchCount++
                    order.add("fetch-$fetchCount")
                    if (fetchCount == 1) gate.withLock {}
                    emptyList()
                },
                deliver = {},
                ack = { order.add("ack") },
            )

        val first = launch { relay.pollOnce(10) }
        runCurrent()
        val second = launch { relay.pollOnce(10) }
        runCurrent()

        assertEquals(
            listOf("fetch-1"),
            order,
            "the second call must not fetch until the first finishes",
        )

        gate.unlock()
        first.join()
        second.join()

        assertEquals(listOf("fetch-1", "fetch-2"), order)
    }

    @Test
    fun poll_keepsPollingOnTheConfiguredInterval() = runTest {
        val fetchLimits = mutableListOf<Int>()
        val relay =
            EnvelopeRelay(
                fetch = {
                    fetchLimits.add(it)
                    emptyList()
                },
                deliver = {},
                ack = {},
            )

        val job = backgroundScope.launch { relay.poll(10, 10.milliseconds) }
        runCurrent()
        assertEquals(1, fetchLimits.size)

        advanceTimeBy(10.milliseconds)
        runCurrent()
        assertEquals(2, fetchLimits.size)

        advanceTimeBy(10.milliseconds)
        runCurrent()
        assertEquals(3, fetchLimits.size)

        job.cancel()
    }

    @Test
    fun poll_aThrowingStoreDoesNotKillTheLoop() = runTest {
        val fetchLimits = mutableListOf<Int>()
        val relay =
            EnvelopeRelay(
                fetch = {
                    fetchLimits.add(it)
                    error("db down")
                },
                deliver = {},
                ack = {},
            )

        val job = backgroundScope.launch { relay.poll(10, 10.milliseconds) }
        runCurrent()
        assertEquals(1, fetchLimits.size)

        advanceTimeBy(10.milliseconds)
        runCurrent()
        assertEquals(2, fetchLimits.size)

        job.cancel()
    }

    @Test
    fun relay_rethrowsCancellation_ratherThanRecordingItAsAFailedEntry() = runTest {
        val acked = mutableListOf<List<String>>()
        val relay =
            EnvelopeRelay(
                deliver = { throw CancellationException("cancelled") },
                ack = { acked.add(it) },
            )

        assertFailsWith<CancellationException> {
            relay.relay(listOf(EventEnvelope("1", RelayTestEvent("a"))))
        }
        assertTrue(acked.isEmpty())
    }

    @Test
    fun poll_isCancellable_theLoopStopsAndNoFurtherFetchHappens() = runTest {
        val fetchLimits = mutableListOf<Int>()
        val relay =
            EnvelopeRelay(
                fetch = {
                    fetchLimits.add(it)
                    emptyList()
                },
                deliver = {},
                ack = {},
            )

        val job = backgroundScope.launch { relay.poll(10, 10.milliseconds) }
        runCurrent()
        assertEquals(1, fetchLimits.size)

        job.cancel()
        advanceTimeBy(50.milliseconds)
        runCurrent()

        assertEquals(1, fetchLimits.size)
    }
}
