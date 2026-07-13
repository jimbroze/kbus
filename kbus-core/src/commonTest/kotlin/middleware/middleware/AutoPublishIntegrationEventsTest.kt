package com.jimbroze.kbus.core.middleware.middleware

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.messages.event.AutoPublishesFrom
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
    fun publishes_the_integration_event_mapped_from_a_registered_domain_event() = runTest {
        val publisher = RecordingIntegrationEventPublisher()
        val middleware =
            AutoPublishIntegrationEvents(mapOf(OrderPlaced::class to OrderPlacedIntegration("")))

        middleware.handle(OrderPlaced("order-1"), contextWith(publisher)) {}

        val published = publisher.publishedEvents.flatten()
        val event = assertIs<OrderPlacedIntegration>(published.single())
        assertEquals("order-1", event.orderId)
    }

    @Test
    fun continues_the_chain_and_returns_the_next_middleware_result() = runTest {
        val middleware =
            AutoPublishIntegrationEvents(mapOf(OrderPlaced::class to OrderPlacedIntegration("")))
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
    fun does_not_publish_for_domain_events_without_a_registered_mapper() = runTest {
        val publisher = RecordingIntegrationEventPublisher()
        val middleware =
            AutoPublishIntegrationEvents(mapOf(OrderPlaced::class to OrderPlacedIntegration("")))
        var handlerCalled = false

        middleware.handle(TestDomainEvent("plain"), contextWith(publisher)) { handlerCalled = true }

        assertTrue(publisher.publishedEvents.isEmpty())
        assertTrue(handlerCalled)
    }

    @Test
    fun from_domain_event_maps_the_domain_event_to_its_integration_event() {
        val integrationEvent = OrderPlacedIntegration("").fromDomainEvent(OrderPlaced("order-3"))

        val event = assertIs<OrderPlacedIntegration>(integrationEvent)
        assertEquals("order-3", event.orderId)
    }
}

// --- Test doubles ---

private class OrderPlaced(val orderId: String) : DomainEvent()

private class OrderPlacedIntegration(val orderId: String) :
    IntegrationEvent(), AutoPublishesFrom<OrderPlaced> {
    override fun fromDomainEvent(event: OrderPlaced): OrderPlacedIntegration =
        OrderPlacedIntegration(event.orderId)
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
