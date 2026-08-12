package com.jimbroze.kbus.core.middleware.middleware

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.RecordingIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.messages.event.dispatch.IntegrationEventMapper
import com.jimbroze.kbus.core.middleware.MiddlewareInvocationContext
import com.jimbroze.kbus.domain.event.DomainEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AutoPublishIntegrationEventsTest {

    @Test
    fun `publishes the integration event a lambda registration maps to`() = runTest {
        val publisher = RecordingIntegrationEventPublisher()
        val middleware =
            AutoPublishIntegrationEvents(
                autoPublish<OrderPlaced> { OrderPlacedIntegration(it.orderId) }
            )

        middleware.handle(OrderPlaced("order-1"), contextWith(publisher)) {}

        val published = publisher.publishedEvents.flatten()
        val event = assertIs<OrderPlacedIntegration>(published.single())
        assertEquals("order-1", event.orderId)
    }

    @Test
    fun `publishes the integration event a mapper object maps to`() = runTest {
        val publisher = RecordingIntegrationEventPublisher()
        val middleware = AutoPublishIntegrationEvents(autoPublish(OrderPlacedMapper))

        middleware.handle(OrderPlaced("order-2"), contextWith(publisher)) {}

        val published = publisher.publishedEvents.flatten()
        val event = assertIs<OrderPlacedIntegration>(published.single())
        assertEquals("order-2", event.orderId)
    }

    @Test
    fun `publishes every integration event registered for a domain event in one batch`() = runTest {
        val publisher = RecordingIntegrationEventPublisher()
        val middleware =
            AutoPublishIntegrationEvents(
                autoPublish(OrderPlacedMapper),
                autoPublish<OrderPlaced> { OrderPlacedAnalytics(it.orderId) },
            )

        middleware.handle(OrderPlaced("order-3"), contextWith(publisher)) {}

        val batch = publisher.publishedEvents.single()
        assertEquals(2, batch.size)
        assertIs<OrderPlacedIntegration>(batch[0])
        assertIs<OrderPlacedAnalytics>(batch[1])
    }

    @Test
    fun `passes the event down the chain and returns what the chain returned`() = runTest {
        val middleware =
            AutoPublishIntegrationEvents(
                autoPublish<OrderPlaced> { OrderPlacedIntegration(it.orderId) }
            )
        var handledMessage: OrderPlaced? = null

        val result =
            middleware.handle(OrderPlaced("order-4"), contextWith()) { message ->
                handledMessage = message
                "handled"
            }

        assertEquals("order-4", handledMessage?.orderId)
        assertEquals("handled", result)
    }

    @Test
    fun `publishes nothing for a domain event with no registration`() = runTest {
        val publisher = RecordingIntegrationEventPublisher()
        val middleware =
            AutoPublishIntegrationEvents(
                autoPublish<OrderPlaced> { OrderPlacedIntegration(it.orderId) }
            )
        var handlerCalled = false

        middleware.handle(TestDomainEvent("plain"), contextWith(publisher)) { handlerCalled = true }

        assertTrue(publisher.publishedEvents.isEmpty())
        assertTrue(handlerCalled)
    }
}

// --- Test doubles ---

private class OrderPlaced(val orderId: String) : DomainEvent()

private class OrderPlacedIntegration(val orderId: String) : IntegrationEvent()

private object OrderPlacedMapper : IntegrationEventMapper<OrderPlaced> {
    override fun fromDomainEvent(event: OrderPlaced) = OrderPlacedIntegration(event.orderId)
}

private class OrderPlacedAnalytics(val orderId: String) : IntegrationEvent()

private fun contextWith(
    publisher: IntegrationEventPublisher = RecordingIntegrationEventPublisher()
): MiddlewareInvocationContext =
    object : MiddlewareInvocationContext {
        override val integrationEventPublisher = publisher
    }
