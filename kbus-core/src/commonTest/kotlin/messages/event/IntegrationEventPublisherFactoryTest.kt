package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.core.fixtures.RecordingDestination
import com.jimbroze.kbus.core.fixtures.RecordingIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.fixtures.TestUnitOfWork
import com.jimbroze.kbus.core.uow.ImmediateOutboxPublisher
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.core.uow.OutboxCoordinator
import com.jimbroze.kbus.core.uow.TransactionalOutbox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

class IntegrationEventPublisherFactoryTest {
    @Test
    fun create_withUnitOfWork_andAnOutboxConfigured_returnsATransactionalOutbox() = runTest {
        val router = EventRouter(listOf(RecordingDestination()))
        val directPublisher = RecordingIntegrationEventPublisher()
        val factory =
            IntegrationEventPublisherFactory(
                OutboxCoordinator(OutboxConfig(RecordingOutboxStore()), router, this),
                directPublisher,
            )

        assertIs<TransactionalOutbox>(factory.create(TestUnitOfWork<Any?>()))
    }

    @Test
    fun create_withUnitOfWork_andNoOutboxConfigured_returnsTheDirectPublisher() = runTest {
        val router = EventRouter(listOf(RecordingDestination()))
        val directPublisher = RecordingIntegrationEventPublisher()
        val factory =
            IntegrationEventPublisherFactory(OutboxCoordinator(null, router, this), directPublisher)

        assertEquals(directPublisher, factory.create(TestUnitOfWork<Any?>()))
    }

    @Test
    fun create_withNull_andAnOutboxConfigured_returnsTheImmediateOutboxPublisher() = runTest {
        val router = EventRouter(listOf(RecordingDestination()))
        val directPublisher = RecordingIntegrationEventPublisher()
        val factory =
            IntegrationEventPublisherFactory(
                OutboxCoordinator(OutboxConfig(RecordingOutboxStore()), router, this),
                directPublisher,
            )

        assertIs<ImmediateOutboxPublisher>(factory.create(null))
    }

    @Test
    fun create_withNull_andNoOutboxConfigured_returnsTheDirectPublisher() = runTest {
        val router = EventRouter(listOf(RecordingDestination()))
        val directPublisher = RecordingIntegrationEventPublisher()
        val factory =
            IntegrationEventPublisherFactory(OutboxCoordinator(null, router, this), directPublisher)

        assertEquals(directPublisher, factory.create(null))
    }

    @Test
    fun create_withNull_returnsTheSameImmediateInstanceAcrossCalls() = runTest {
        val router = EventRouter(listOf(RecordingDestination()))
        val directPublisher = RecordingIntegrationEventPublisher()
        val factory =
            IntegrationEventPublisherFactory(
                OutboxCoordinator(OutboxConfig(RecordingOutboxStore()), router, this),
                directPublisher,
            )

        assertSame(factory.create(null), factory.create(null))
    }
}
