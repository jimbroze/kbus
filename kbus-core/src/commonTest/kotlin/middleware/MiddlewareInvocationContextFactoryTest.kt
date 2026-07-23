package com.jimbroze.kbus.core.middleware

import com.jimbroze.kbus.core.fixtures.RecordingIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.fixtures.TestPublisherFactories
import com.jimbroze.kbus.core.fixtures.TestUnitOfWork
import com.jimbroze.kbus.core.fixtures.noOutboxPublisherFactory
import com.jimbroze.kbus.core.fixtures.testInvocation
import com.jimbroze.kbus.core.uow.ImmediateOutboxPublisher
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.core.uow.TransactionalOutbox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlinx.coroutines.test.runTest

class MiddlewareInvocationContextFactoryTest {
    @Test
    fun contextFor_an_invocation_with_an_outbox_publisher_exposes_that_outbox() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val outbox =
            TransactionalOutbox(RecordingOutboxStore(), basePublisher, this, TestUnitOfWork<Any?>())
        val invocation = testInvocation<Any?>(publisher = outbox)
        val factory = MiddlewareInvocationContextFactory(noOutboxPublisherFactory(basePublisher))

        assertEquals(outbox, factory.contextFor(invocation).integrationEventPublisher)
    }

    @Test
    fun contextFor_an_invocation_using_the_base_publisher_exposes_it() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val invocation = testInvocation<Any?>(publisher = basePublisher)
        val factory = MiddlewareInvocationContextFactory(noOutboxPublisherFactory(basePublisher))

        assertEquals(basePublisher, factory.contextFor(invocation).integrationEventPublisher)
    }

    @Test
    fun contextFor_null_exposes_the_base_publisher() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val factory = MiddlewareInvocationContextFactory(noOutboxPublisherFactory(basePublisher))

        assertEquals(basePublisher, factory.contextFor(null).integrationEventPublisher)
    }

    @Test
    fun contextFor_null_withAnOutboxConfigured_exposesTheImmediateOutboxPublisher() = runTest {
        val publishers = TestPublisherFactories(outboxConfig = OutboxConfig(RecordingOutboxStore()))
        val factory = publishers.contextFactory

        assertIs<ImmediateOutboxPublisher>(factory.contextFor(null).integrationEventPublisher)
    }

    @Test
    fun resolution_is_per_call_not_cached() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val outbox =
            TransactionalOutbox(RecordingOutboxStore(), basePublisher, this, TestUnitOfWork<Any?>())
        val invocationWithOutbox = testInvocation<Any?>(publisher = outbox)
        val invocationWithoutOutbox = testInvocation<Any?>(publisher = basePublisher)
        val factory = MiddlewareInvocationContextFactory(noOutboxPublisherFactory(basePublisher))

        val firstPublisher = factory.contextFor(invocationWithOutbox).integrationEventPublisher
        val secondPublisher = factory.contextFor(invocationWithoutOutbox).integrationEventPublisher

        assertNotEquals(firstPublisher, secondPublisher)
    }
}
