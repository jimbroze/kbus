package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.core.fixtures.RecordingDestination
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.fixtures.TestUnitOfWorkFactory
import com.jimbroze.kbus.core.fixtures.noOutboxPublisherFactory
import com.jimbroze.kbus.core.messages.event.publish.DirectPublisher
import com.jimbroze.kbus.core.messages.event.publish.IntegrationEventPublisherFactory
import com.jimbroze.kbus.core.messages.event.routing.EventRouter
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.core.uow.OutboxCoordinator
import com.jimbroze.kbus.core.uow.TransactionalOutbox
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CommandInvocationFactoryTest {
    @Test
    fun `gives an invocation the base publisher when no outbox is configured`() = runTest {
        val basePublisher = DirectPublisher(EventRouter(emptyList()), this)
        val factory =
            CommandInvocationFactory(
                TestUnitOfWorkFactory(),
                noOutboxPublisherFactory(backgroundScope, basePublisher),
            )

        val invocation = factory.create<Any?>()

        assertSame(basePublisher, invocation.integrationEventPublisher)
    }

    @Test
    fun `registers no unit-of-work phases when no outbox is configured`() = runTest {
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        val factory =
            CommandInvocationFactory(unitOfWorkFactory, noOutboxPublisherFactory(backgroundScope))

        factory.create<Any?>()

        assertTrue(unitOfWorkFactory.unitOfWork.secondaryWork.isEmpty())
        assertTrue(unitOfWorkFactory.unitOfWork.postCommitWork.isEmpty())
    }

    @Test
    fun `gives an invocation the outbox to publish through when one is configured`() = runTest {
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        val factory =
            CommandInvocationFactory(
                unitOfWorkFactory,
                IntegrationEventPublisherFactory(
                    OutboxCoordinator(
                        OutboxConfig(RecordingOutboxStore()),
                        EventRouter(listOf(RecordingDestination())),
                        this,
                    ),
                    DirectPublisher(EventRouter(emptyList()), this),
                ),
            )

        val invocation = factory.create<Any?>()

        assertIs<TransactionalOutbox>(invocation.integrationEventPublisher)
    }

    @Test
    fun `gives the outbox the unit of work of the invocation it belongs to`() = runTest {
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        val factory =
            CommandInvocationFactory(
                unitOfWorkFactory,
                IntegrationEventPublisherFactory(
                    OutboxCoordinator(
                        OutboxConfig(RecordingOutboxStore()),
                        EventRouter(listOf(RecordingDestination())),
                        this,
                    ),
                    DirectPublisher(EventRouter(emptyList()), this),
                ),
            )

        val invocation = factory.create<Any?>()

        assertTrue(unitOfWorkFactory.unitOfWork === invocation.unitOfWork)
        assertTrue(unitOfWorkFactory.unitOfWork.secondaryWork.isNotEmpty())
    }
}
