package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.RecordingDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

private class DirectPublisherTestEvent(val name: String) : IntegrationEvent()

class DirectPublisherTest {
    @Test
    fun publish_mintsOneEnvelopePerEventAndRoutesThem() = runTest {
        val destination = RecordingDestination()
        val publisher = DirectPublisher(EventRouter(listOf(destination)))

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
        val publisher = DirectPublisher(EventRouter(listOf(destination)))

        publisher.publish(listOf(DirectPublisherTestEvent("a"), DirectPublisherTestEvent("b")))

        assertEquals(2, destination.delivered.map { it.id }.toSet().size)
    }
}
