package com.jimbroze.kbus.core.messages.event.publish

import com.jimbroze.kbus.core.fixtures.RecordingDestination
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.fixtures.TestUnitOfWork
import com.jimbroze.kbus.core.messages.event.routing.EventRouter
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
    fun `builds a transactional outbox for a unit of work when an outbox is configured`() =
        runTest {
            val router = EventRouter(listOf(RecordingDestination()))
            val directPublisher = DirectPublisher(router, this)
            val factory =
                IntegrationEventPublisherFactory(
                    OutboxCoordinator(OutboxConfig(RecordingOutboxStore()), router, this),
                    directPublisher,
                )

            assertIs<TransactionalOutbox>(factory.create(TestUnitOfWork<Any?>()))
        }

    @Test
    fun `builds the direct publisher for a unit of work when no outbox is configured`() = runTest {
        val router = EventRouter(listOf(RecordingDestination()))
        val directPublisher = DirectPublisher(router, this)
        val factory =
            IntegrationEventPublisherFactory(OutboxCoordinator(null, router, this), directPublisher)

        assertEquals(directPublisher, factory.create(TestUnitOfWork<Any?>()))
    }

    @Test
    fun `builds the immediate outbox publisher outside a unit of work when an outbox is configured`() =
        runTest {
            val router = EventRouter(listOf(RecordingDestination()))
            val directPublisher = DirectPublisher(router, this)
            val factory =
                IntegrationEventPublisherFactory(
                    OutboxCoordinator(OutboxConfig(RecordingOutboxStore()), router, this),
                    directPublisher,
                )

            assertIs<ImmediateOutboxPublisher>(factory.create(null))
        }

    @Test
    fun `builds the direct publisher outside a unit of work when no outbox is configured`() =
        runTest {
            val router = EventRouter(listOf(RecordingDestination()))
            val directPublisher = DirectPublisher(router, this)
            val factory =
                IntegrationEventPublisherFactory(
                    OutboxCoordinator(null, router, this),
                    directPublisher,
                )

            assertEquals(directPublisher, factory.create(null))
        }

    @Test
    fun `reuses one immediate outbox publisher across calls outside a unit of work`() = runTest {
        val router = EventRouter(listOf(RecordingDestination()))
        val directPublisher = DirectPublisher(router, this)
        val factory =
            IntegrationEventPublisherFactory(
                OutboxCoordinator(OutboxConfig(RecordingOutboxStore()), router, this),
                directPublisher,
            )

        assertSame(factory.create(null), factory.create(null))
    }
}
