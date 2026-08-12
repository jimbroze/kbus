package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.core.fixtures.CapturingContextMiddleware
import com.jimbroze.kbus.core.fixtures.EmptyIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.PublishingDomainEventHandler
import com.jimbroze.kbus.core.fixtures.PublishingIntegrationEventHandler
import com.jimbroze.kbus.core.fixtures.RecordingDestination
import com.jimbroze.kbus.core.fixtures.RecordingIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import com.jimbroze.kbus.core.fixtures.TestUnitOfWork
import com.jimbroze.kbus.core.fixtures.emptyContextFactory
import com.jimbroze.kbus.core.fixtures.noOutboxPublisherFactory
import com.jimbroze.kbus.core.fixtures.testInvocation
import com.jimbroze.kbus.core.messages.event.dispatch.EventDispatcher
import com.jimbroze.kbus.core.messages.event.publish.DirectPublisher
import com.jimbroze.kbus.core.messages.event.routing.EventRouter
import com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareInvocationContextFactory
import com.jimbroze.kbus.core.uow.TransactionalOutbox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EventDispatcherPublisherWiringTest {

    @Test
    fun `gives a domain event's middleware the outbox its invocation publishes through`() =
        runTest {
            val capturingMiddleware = CapturingContextMiddleware()
            val store = RecordingOutboxStore()
            val outbox =
                TransactionalOutbox(
                    store,
                    EventRouter(listOf(RecordingDestination())),
                    this,
                    TestUnitOfWork<Any?>(),
                )
            val invocation = testInvocation<Any?>(publisher = outbox)
            val dispatcher =
                EventDispatcher(
                    { _, _ -> emptyList() },
                    listOf(capturingMiddleware),
                    this,
                    contextFactory = emptyContextFactory(backgroundScope),
                )

            dispatcher.dispatchDomainEvent(TestDomainEvent("test"), invocation)

            assertEquals(outbox, capturingMiddleware.capturedContext?.integrationEventPublisher)
        }

    @Test
    fun `gives a domain event's middleware the base publisher when its invocation has no outbox`() =
        runTest {
            val capturingMiddleware = CapturingContextMiddleware()
            val invocation = testInvocation<Any?>()
            val dispatcher =
                EventDispatcher(
                    { _, _ -> emptyList() },
                    listOf(capturingMiddleware),
                    this,
                    contextFactory = emptyContextFactory(backgroundScope),
                )

            dispatcher.dispatchDomainEvent(TestDomainEvent("test"), invocation)

            assertEquals(
                EmptyIntegrationEventPublisher,
                capturingMiddleware.capturedContext?.integrationEventPublisher,
            )
        }

    @Test
    fun `gives an integration event's middleware the base publisher`() = runTest {
        val capturingMiddleware = CapturingContextMiddleware()
        val basePublisher = DirectPublisher(EventRouter(emptyList()), this)
        val dispatcher =
            EventDispatcher(
                { _, _ -> emptyList() },
                listOf(capturingMiddleware),
                this,
                contextFactory =
                    MiddlewareInvocationContextFactory(
                        noOutboxPublisherFactory(backgroundScope, basePublisher)
                    ),
            )

        dispatcher.dispatchIntegrationEvent(TestIntegrationEvent("test"))

        assertEquals(basePublisher, capturingMiddleware.capturedContext?.integrationEventPublisher)
    }

    @Test
    fun `builds a domain handler with the publisher its invocation carries`() = runTest {
        val recordingPublisher = RecordingIntegrationEventPublisher()
        val env = EventDispatchEnvironment(this, recordingPublisher)
        env.withDomainHandlers(PublishingDomainEventHandler(recordingPublisher))

        env.dispatch(TestDomainEvent("via-domain-handler"))

        val published = recordingPublisher.publishedEvents.flatten()
        assertEquals(1, published.size)
        assertEquals("via-domain-handler", (published.single() as TestIntegrationEvent).name)
    }

    @Test
    fun `builds an integration handler with the publisher its context carries`() = runTest {
        val destination = RecordingDestination()
        val dispatcher =
            EventDispatcher(
                { _, _ -> emptyList() },
                emptyList(),
                this,
                contextFactory =
                    MiddlewareInvocationContextFactory(
                        noOutboxPublisherFactory(
                            backgroundScope,
                            DirectPublisher(EventRouter(listOf(destination)), this),
                        )
                    ),
            )

        dispatcher.dispatchIntegrationEvent(
            TestIntegrationEvent("test"),
            { listOf(PublishingIntegrationEventHandler(it.integrationEventPublisher)) },
        )
        advanceUntilIdle()

        val published = destination.delivered.map { it.event }
        assertEquals(1, published.size)
        assertEquals("published-by-test", (published.single() as TestIntegrationEvent).name)
    }
}
