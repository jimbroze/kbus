package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.ErrorStrategy
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.RecordingDestination
import com.jimbroze.kbus.core.messages.event.publish.DirectPublisher
import com.jimbroze.kbus.core.messages.event.routing.AggregateException
import com.jimbroze.kbus.core.messages.event.routing.EventRouter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** [ErrorStrategy.FailFast] so publish awaits routing and a destination failure propagates. */
private class DirectPublisherTestEvent(val name: String) : IntegrationEvent() {
    override val errorStrategy = ErrorStrategy.FailFast
}

/**
 * Default [ErrorStrategy.FireAndForget]: the producer said it doesn't care, so publish shouldn't
 * wait.
 */
private class FireAndForgetTestEvent(val name: String) : IntegrationEvent()

@OptIn(ExperimentalCoroutinesApi::class)
class DirectPublisherTest {
    @Test
    fun publish_mintsOneEnvelopePerEventAndRoutesThem() = runTest {
        val destination = RecordingDestination()
        val publisher = DirectPublisher(EventRouter(listOf(destination)), this)

        publisher.publish(listOf(DirectPublisherTestEvent("a"), DirectPublisherTestEvent("b")))

        assertEquals(2, destination.delivered.size)
        assertEquals(
            listOf("a", "b"),
            destination.delivered.map { (it.event as DirectPublisherTestEvent).name },
        )
    }

    @Test
    fun publish_mintsDistinctIds() = runTest {
        val destination = RecordingDestination()
        val publisher = DirectPublisher(EventRouter(listOf(destination)), this)

        publisher.publish(listOf(DirectPublisherTestEvent("a"), DirectPublisherTestEvent("b")))

        assertEquals(2, destination.delivered.map { it.id }.toSet().size)
    }

    @Test
    fun publish_awaitsNonFireAndForgetEventsAndPropagatesADestinationFailure() = runTest {
        val destination = RecordingDestination()
        destination.failure = IllegalStateException("boom")
        val publisher = DirectPublisher(EventRouter(listOf(destination)), this)

        assertFailsWith<AggregateException> {
            publisher.publish(listOf(DirectPublisherTestEvent("boom")))
        }
    }

    @Test
    fun publish_returnsBeforeFireAndForgetEventsAreRouted() = runTest {
        val destination = RecordingDestination()
        val publisher = DirectPublisher(EventRouter(listOf(destination)), this)

        publisher.publish(listOf(FireAndForgetTestEvent("async")))

        assertTrue(
            destination.delivered.isEmpty(),
            "FireAndForget routing is launched, not awaited",
        )

        advanceUntilIdle()
        assertEquals(
            listOf("async"),
            destination.delivered.map { (it.event as FireAndForgetTestEvent).name },
        )
    }

    @Test
    fun publish_makesExactlyOneRouteCallForAHomogeneousFireAndForgetBatch() = runTest {
        val destination = RecordingDestination()
        val publisher = DirectPublisher(EventRouter(listOf(destination)), this)

        publisher.publish(listOf(FireAndForgetTestEvent("a"), FireAndForgetTestEvent("b")))
        advanceUntilIdle()

        assertEquals(1, destination.deliveredCalls.size)
    }

    @Test
    fun publish_makesExactlyOneRouteCallForAHomogeneousAwaitedBatch() = runTest {
        val destination = RecordingDestination()
        val publisher = DirectPublisher(EventRouter(listOf(destination)), this)

        publisher.publish(listOf(DirectPublisherTestEvent("a"), DirectPublisherTestEvent("b")))

        assertEquals(1, destination.deliveredCalls.size)
    }
}
