package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.core.fixtures.RecordingIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import com.jimbroze.kbus.core.fixtures.testInvocation
import com.jimbroze.kbus.core.uow.TransactionOutbox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class IntegrationPublisherFactoryTest {
    @Test
    fun busAccessFor_dispatches_through_the_invocations_publisher_when_present() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val store = RecordingOutboxStore()
        val outbox = TransactionOutbox(store, RecordingIntegrationEventPublisher(), this)
        val invocation = testInvocation<Any?>(publisher = outbox)
        val factory = IntegrationPublisherFactory(basePublisher)

        factory.busAccessFor(invocation).dispatch(TestIntegrationEvent("via-outbox"))
        outbox.flush()

        assertEquals(1, store.saved.size)
        assertEquals("via-outbox", (store.saved.single().event as TestIntegrationEvent).name)
        assertEquals(0, basePublisher.publishedEvents.size)
    }

    @Test
    fun busAccessFor_dispatches_through_the_base_publisher_for_an_invocation_using_it() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val invocation = testInvocation<Any?>(publisher = basePublisher)
        val factory = IntegrationPublisherFactory(basePublisher)

        factory.busAccessFor(invocation).dispatch(TestIntegrationEvent("via-base"))

        assertEquals(1, basePublisher.publishedEvents.flatten().size)
        assertEquals(
            "via-base",
            (basePublisher.publishedEvents.flatten().single() as TestIntegrationEvent).name,
        )
    }

    @Test
    fun busAccessFor_dispatches_through_the_base_publisher_for_a_null_invocation() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val factory = IntegrationPublisherFactory(basePublisher)

        factory.busAccessFor(null).dispatch(TestIntegrationEvent("via-base"))

        assertEquals(1, basePublisher.publishedEvents.flatten().size)
    }

    @Test
    fun busAccessFor_passes_the_single_event_through_as_a_one_element_list() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val factory = IntegrationPublisherFactory(basePublisher)

        factory.busAccessFor(null).dispatch(TestIntegrationEvent("solo"))

        assertEquals(
            listOf(listOf("solo")),
            basePublisher.publishedEvents.map {
                it.map { event -> (event as TestIntegrationEvent).name }
            },
        )
    }
}
