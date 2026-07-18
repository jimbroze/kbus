package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.RecordingIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.fixtures.TestUnitOfWorkFactory
import com.jimbroze.kbus.core.uow.TransactionOutbox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

private class InvocationTestEvent(val name: String) : IntegrationEvent()

@OptIn(ExperimentalCoroutinesApi::class)
class CommandInvocationFactoryTest {
    @Test
    fun create_without_an_outbox_uses_the_base_publisher() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val factory = CommandInvocationFactory(TestUnitOfWorkFactory(), basePublisher)

        val invocation = factory.create<Any?>()

        assertSame(basePublisher, invocation.integrationEventPublisher)
    }

    @Test
    fun create_without_an_outbox_registers_no_phase_hooks() = runTest {
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        val factory =
            CommandInvocationFactory(unitOfWorkFactory, RecordingIntegrationEventPublisher())

        factory.create<Any?>()

        assertTrue(unitOfWorkFactory.unitOfWork.secondaryWork.isEmpty())
        assertTrue(unitOfWorkFactory.unitOfWork.postCommitWork.isEmpty())
    }

    @Test
    fun create_with_an_outbox_uses_the_outbox_as_the_publisher() = runTest {
        val outbox =
            TransactionOutbox(RecordingOutboxStore(), RecordingIntegrationEventPublisher(), this)
        val factory =
            CommandInvocationFactory(
                TestUnitOfWorkFactory(),
                RecordingIntegrationEventPublisher(),
                outboxFactory = { outbox },
            )

        val invocation = factory.create<Any?>()

        assertSame(outbox, invocation.integrationEventPublisher)
    }

    @Test
    fun create_with_an_outbox_registers_flush_as_secondary_work() = runTest {
        val store = RecordingOutboxStore()
        val outbox = TransactionOutbox(store, RecordingIntegrationEventPublisher(), this)
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        val factory =
            CommandInvocationFactory(
                unitOfWorkFactory,
                RecordingIntegrationEventPublisher(),
                outboxFactory = { outbox },
            )

        val invocation = factory.create<Any?>()
        invocation.integrationEventPublisher.publish(listOf(InvocationTestEvent("a")))

        assertEquals(1, unitOfWorkFactory.unitOfWork.secondaryWork.size)
        assertTrue(store.saved.isEmpty(), "Not saved until the secondary work runs")

        unitOfWorkFactory.unitOfWork.secondaryWork.single().invoke()

        assertEquals(1, store.saved.size)
    }

    @Test
    fun create_with_an_outbox_registers_drain_as_post_commit_work() = runTest {
        val store = RecordingOutboxStore()
        val realPublisher = RecordingIntegrationEventPublisher()
        val outbox = TransactionOutbox(store, realPublisher, this)
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        val factory =
            CommandInvocationFactory(
                unitOfWorkFactory,
                RecordingIntegrationEventPublisher(),
                outboxFactory = { outbox },
            )

        val invocation = factory.create<Any?>()
        invocation.integrationEventPublisher.publish(listOf(InvocationTestEvent("a")))

        assertEquals(1, unitOfWorkFactory.unitOfWork.postCommitWork.size)

        unitOfWorkFactory.unitOfWork.postCommitWork.single().invoke()
        advanceUntilIdle()

        assertEquals(1, realPublisher.publishedEvents.flatten().size)
    }

    @Test
    fun create_with_drainAfterCommit_false_registers_no_post_commit_hook() = runTest {
        val outbox =
            TransactionOutbox(RecordingOutboxStore(), RecordingIntegrationEventPublisher(), this)
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        val factory =
            CommandInvocationFactory(
                unitOfWorkFactory,
                RecordingIntegrationEventPublisher(),
                { outbox },
                drainAfterCommit = false,
            )

        factory.create<Any?>()

        assertEquals(1, unitOfWorkFactory.unitOfWork.secondaryWork.size)
        assertTrue(unitOfWorkFactory.unitOfWork.postCommitWork.isEmpty())
    }
}
