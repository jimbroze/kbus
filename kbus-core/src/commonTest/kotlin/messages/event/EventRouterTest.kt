package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.EventDestination
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.RecordingDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun route_deliversOnlyToAcceptingDestinations() = runTest {
        val accepting = RecordingDestination()
        val rejecting =
            object : EventDestination {
                override val name = "rejecting"

                override fun accepts(event: IntegrationEvent) = false

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

        assertFailsWith<MultipleException> {
            router.route(listOf(EventEnvelope.of(RouterTestEvent("test"))))
        }

        assertEquals(1, healthy.delivered.size)
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
