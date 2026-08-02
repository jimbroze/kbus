package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.EventDestination
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.RecordingDestination
import com.jimbroze.kbus.core.messages.event.routing.AggregateException
import com.jimbroze.kbus.core.messages.event.routing.EventRouter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

private class RouterTestEvent(val name: String) : IntegrationEvent()

@OptIn(ExperimentalCoroutinesApi::class)
class EventRouterTest {
    @Test
    fun route_emitsToObservers_evenWithDestinationsPresent() = runTest {
        val router = EventRouter(listOf(RecordingDestination()))
        val received = mutableListOf<RouterTestEvent>()
        val flow = router.observerRegistry.observableFor(RouterTestEvent::class)
        val job = launch { flow.take(1).toList(received) }
        yield()

        router.route(listOf(EventEnvelope.of(RouterTestEvent("test"))))
        advanceUntilIdle()
        job.join()

        assertEquals("test", received.single().name)
    }

    @Test
    fun route_reRoutingTheSameEnvelope_reEmitsToObservers_soObserveIsAtLeastOnce() = runTest {
        // A failed destination leaves the entry unpublished and the poller re-routes it, so the
        // same envelope reaches observers again. Observation is at-least-once, not exactly-once.
        val router = EventRouter(listOf(RecordingDestination()))
        val received = mutableListOf<RouterTestEvent>()
        val flow = router.observerRegistry.observableFor(RouterTestEvent::class)
        val job = launch { flow.take(2).toList(received) }
        yield()

        val envelope = EventEnvelope.of(RouterTestEvent("retried"))
        router.route(listOf(envelope))
        router.route(listOf(envelope))
        advanceUntilIdle()
        job.join()

        assertEquals(listOf("retried", "retried"), received.map { it.name })
    }

    @Test
    fun route_deliversOnlyToAcceptingDestinations() = runTest {
        val accepting = RecordingDestination()
        val rejecting =
            object : EventDestination {
                override val name = "rejecting"

                override fun appliesTo(event: IntegrationEvent) = false

                override suspend fun deliver(envelopes: List<EventEnvelope>) {
                    error("should never be called")
                }
            }
        val router = EventRouter(listOf(accepting, rejecting))

        router.route(listOf(EventEnvelope.of(RouterTestEvent("test"))))

        assertEquals(1, accepting.delivered.size)
    }

    @Test
    fun route_deliversToEveryAcceptingDestination() = runTest {
        val first = RecordingDestination("first")
        val second = RecordingDestination("second")
        val router = EventRouter(listOf(first, second))

        router.route(listOf(EventEnvelope.of(RouterTestEvent("test"))))

        assertEquals(1, first.delivered.size)
        assertEquals(1, second.delivered.size)
    }

    @Test
    fun route_oneDestinationThrowing_stillDeliversToTheOthersAndThenThrows() = runTest {
        val healthy = RecordingDestination()
        val sick = RecordingDestination().apply { failure = IllegalStateException("sick") }
        val router = EventRouter(listOf(sick, healthy))

        assertFailsWith<AggregateException> {
            router.route(listOf(EventEnvelope.of(RouterTestEvent("test"))))
        }

        assertEquals(1, healthy.delivered.size)
    }

    @Test
    fun route_doesNotMakeOneDestinationWaitForAnother() = runTest {
        val first = RecordingDestination("first").apply { beforeDeliver = { delay(10.seconds) } }
        val second = RecordingDestination("second").apply { beforeDeliver = { delay(10.seconds) } }
        val router = EventRouter(listOf(first, second))

        router.route(listOf(EventEnvelope.of(RouterTestEvent("test"))))

        assertEquals(1, first.delivered.size)
        assertEquals(1, second.delivered.size)
        assertEquals(10.seconds.inWholeMilliseconds, testScheduler.currentTime)
    }

    @Test
    fun route_aDestinationBeingCancelled_throwsCancellation_notAggregate() = runTest {
        val cancelled =
            RecordingDestination("cancelled").apply {
                failure = CancellationException("shutting down")
            }
        val router = EventRouter(listOf(cancelled, RecordingDestination()))

        assertFailsWith<CancellationException> {
            router.route(listOf(EventEnvelope.of(RouterTestEvent("test"))))
        }
    }

    @Test
    fun route_allDestinationsSucceeding_throwsNothing() = runTest {
        val router = EventRouter(listOf(RecordingDestination(), RecordingDestination()))

        router.route(listOf(EventEnvelope.of(RouterTestEvent("test"))))
    }

    @Test
    fun route_withNoDestinations_isANoOpThatStillEmits() = runTest {
        val router = EventRouter(emptyList())
        val received = mutableListOf<RouterTestEvent>()
        val flow = router.observerRegistry.observableFor(RouterTestEvent::class)
        val job = launch { flow.take(1).toList(received) }
        yield()

        router.route(listOf(EventEnvelope.of(RouterTestEvent("test"))))
        advanceUntilIdle()
        job.join()

        assertEquals("test", received.single().name)
    }
}
