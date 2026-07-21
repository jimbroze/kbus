package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.core.fixtures.RecordingIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.fixtures.TestUnitOfWork
import com.jimbroze.kbus.core.uow.ImmediateOutboxPublisher
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.core.uow.TransactionalOutbox
import com.jimbroze.kbus.core.uow.TransactionalOutboxFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

class IntegrationEventPublisherFactoryTest {
    @Test
    fun create_withUnitOfWork_andAnOutboxConfigured_returnsATransactionalOutbox() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val factory =
            IntegrationEventPublisherFactory(
                TransactionalOutboxFactory(
                    OutboxConfig(RecordingOutboxStore()),
                    basePublisher,
                    this,
                ),
                basePublisher,
            )

        assertIs<TransactionalOutbox>(factory.create(TestUnitOfWork<Any?>()))
    }

    @Test
    fun create_withUnitOfWork_andNoOutboxConfigured_returnsTheBasePublisher() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val factory =
            IntegrationEventPublisherFactory(
                TransactionalOutboxFactory(null, basePublisher, this),
                basePublisher,
            )

        assertEquals(basePublisher, factory.create(TestUnitOfWork<Any?>()))
    }

    @Test
    fun create_withNull_andAnOutboxConfigured_returnsTheImmediateOutboxPublisher() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val factory =
            IntegrationEventPublisherFactory(
                TransactionalOutboxFactory(
                    OutboxConfig(RecordingOutboxStore()),
                    basePublisher,
                    this,
                ),
                basePublisher,
            )

        assertIs<ImmediateOutboxPublisher>(factory.create(null))
    }

    @Test
    fun create_withNull_andNoOutboxConfigured_returnsTheBasePublisher() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val factory =
            IntegrationEventPublisherFactory(
                TransactionalOutboxFactory(null, basePublisher, this),
                basePublisher,
            )

        assertEquals(basePublisher, factory.create(null))
    }

    @Test
    fun create_withNull_returnsTheSameImmediateInstanceAcrossCalls() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val factory =
            IntegrationEventPublisherFactory(
                TransactionalOutboxFactory(
                    OutboxConfig(RecordingOutboxStore()),
                    basePublisher,
                    this,
                ),
                basePublisher,
            )

        assertSame(factory.create(null), factory.create(null))
    }
}
