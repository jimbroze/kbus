package com.jimbroze.kbus.core.middleware.middleware

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import com.jimbroze.kbus.core.messages.event.AutoPublishesIntegrationEvent
import com.jimbroze.kbus.core.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.core.middleware.MiddlewareInvocationContext
import com.jimbroze.kbus.domain.event.DomainEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AutoPublishIntegrationEventsTest {

    @Test
    fun publishes_the_integration_event_produced_by_an_auto_publishing_domain_event() = runTest {
        val publisher = RecordingIntegrationEventPublisher()
        val middleware = AutoPublishIntegrationEvents()

        middleware.handle(OrderPlaced("order-1"), contextWith(publisher)) {}

        val published = publisher.publishedEvents.flatten()
        assertEquals(1, published.size)
        val event = assertIs<TestIntegrationEvent>(published.single())
        assertEquals("order-1", event.name)
    }

    @Test
    fun continues_the_chain_and_returns_the_next_middleware_result() = runTest {
        val middleware = AutoPublishIntegrationEvents()
        var handledMessage: OrderPlaced? = null

        val result =
            middleware.handle(OrderPlaced("order-2"), contextWith()) { message ->
                handledMessage = message
                "handled"
            }

        assertEquals("order-2", handledMessage?.orderId)
        assertEquals("handled", result)
    }

    @Test
    fun does_not_publish_for_messages_that_do_not_auto_publish() = runTest {
        val publisher = RecordingIntegrationEventPublisher()
        val middleware = AutoPublishIntegrationEvents()
        var handlerCalled = false

        middleware.handle(TestDomainEvent("plain"), contextWith(publisher)) { handlerCalled = true }

        assertTrue(publisher.publishedEvents.isEmpty())
        assertTrue(handlerCalled)
    }

    @Test
    fun interface_publish_default_publishes_the_mapped_event_through_the_publisher() = runTest {
        val publisher = RecordingIntegrationEventPublisher()

        OrderPlaced("order-3").publish(publisher)

        val published = publisher.publishedEvents.flatten()
        val event = assertIs<TestIntegrationEvent>(published.single())
        assertEquals("order-3", event.name)
    }
}

// --- Test doubles ---

private class OrderPlaced(val orderId: String) :
    DomainEvent(), AutoPublishesIntegrationEvent<TestIntegrationEvent> {
    override fun toIntegrationEvent(): TestIntegrationEvent = TestIntegrationEvent(orderId)
}

private class RecordingIntegrationEventPublisher : IntegrationEventPublisher {
    val publishedEvents = mutableListOf<List<IntegrationEvent>>()

    override suspend fun publish(events: List<IntegrationEvent>) {
        publishedEvents.add(events)
    }
}

private fun contextWith(
    publisher: IntegrationEventPublisher = RecordingIntegrationEventPublisher()
): MiddlewareInvocationContext =
    object : MiddlewareInvocationContext {
        override val integrationEventPublisher = publisher
    }
