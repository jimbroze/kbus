package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.core.fixtures.RecordingDestination
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.fixtures.TestUnitOfWorkFactory
import com.jimbroze.kbus.core.fixtures.noOutboxPublisherFactory
import com.jimbroze.kbus.core.messages.event.publish.DirectPublisher
import com.jimbroze.kbus.core.messages.event.publish.IntegrationEventPublisherFactory
import com.jimbroze.kbus.core.messages.event.routing.EventRouter
import com.jimbroze.kbus.core.module.BoundedContextId
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
    fun create_without_an_outbox_uses_the_base_publisher() = runTest {
        val basePublisher = DirectPublisher(EventRouter(emptyList()), this)
        val factory =
            CommandInvocationFactory(
                TestUnitOfWorkFactory(),
                noOutboxPublisherFactory(backgroundScope, basePublisher),
            )

        val invocation = factory.create<Any?>(BoundedContextId.DEFAULT)

        assertSame(basePublisher, invocation.integrationEventPublisher)
    }

    @Test
    fun create_without_an_outbox_registers_no_phase_hooks() = runTest {
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        val factory =
            CommandInvocationFactory(unitOfWorkFactory, noOutboxPublisherFactory(backgroundScope))

        factory.create<Any?>(BoundedContextId.DEFAULT)

        assertTrue(unitOfWorkFactory.unitOfWork.secondaryWork.isEmpty())
        assertTrue(unitOfWorkFactory.unitOfWork.postCommitWork.isEmpty())
    }

    @Test
    fun create_with_an_outbox_uses_the_outbox_as_the_publisher() = runTest {
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

        val invocation = factory.create<Any?>(BoundedContextId.DEFAULT)

        assertIs<TransactionalOutbox>(invocation.integrationEventPublisher)
    }

    @Test
    fun create_with_an_outbox_passes_the_invocations_unit_of_work_to_the_outbox() = runTest {
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

        val invocation = factory.create<Any?>(BoundedContextId.DEFAULT)

        assertTrue(unitOfWorkFactory.unitOfWork === invocation.unitOfWork)
        assertTrue(unitOfWorkFactory.unitOfWork.secondaryWork.isNotEmpty())
    }
}
