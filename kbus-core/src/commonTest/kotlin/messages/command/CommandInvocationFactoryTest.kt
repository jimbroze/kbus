package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.core.fixtures.RecordingIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.fixtures.TestUnitOfWorkFactory
import com.jimbroze.kbus.core.uow.TransactionOutbox
import com.jimbroze.kbus.core.uow.UnitOfWork
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

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
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        lateinit var outbox: TransactionOutbox
        val factory =
            CommandInvocationFactory(unitOfWorkFactory, RecordingIntegrationEventPublisher()) {
                unitOfWork ->
                TransactionOutbox(
                        RecordingOutboxStore(),
                        RecordingIntegrationEventPublisher(),
                        this,
                        unitOfWork,
                    )
                    .also { outbox = it }
            }

        val invocation = factory.create<Any?>()

        assertSame(outbox, invocation.integrationEventPublisher)
    }

    @Test
    fun create_with_an_outbox_passes_the_invocations_unit_of_work_to_the_outbox_factory() =
        runTest {
            val unitOfWorkFactory = TestUnitOfWorkFactory()
            var receivedUnitOfWork: UnitOfWork<*>? = null
            val factory =
                CommandInvocationFactory(unitOfWorkFactory, RecordingIntegrationEventPublisher()) {
                    unitOfWork ->
                    receivedUnitOfWork = unitOfWork
                    TransactionOutbox(
                        RecordingOutboxStore(),
                        RecordingIntegrationEventPublisher(),
                        this,
                        unitOfWork,
                    )
                }

            val invocation = factory.create<Any?>()

            assertSame(invocation.unitOfWork, receivedUnitOfWork)
        }
}
