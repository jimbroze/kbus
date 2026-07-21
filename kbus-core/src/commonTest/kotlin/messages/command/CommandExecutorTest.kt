package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.core.fixtures.CapturingContextMiddleware
import com.jimbroze.kbus.core.fixtures.DispatchingCommand
import com.jimbroze.kbus.core.fixtures.DispatchingCommandHandler
import com.jimbroze.kbus.core.fixtures.RecordingIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.fixtures.ReturnCommand
import com.jimbroze.kbus.core.fixtures.ReturnCommandHandler
import com.jimbroze.kbus.core.fixtures.TestCommandDependenciesFactory
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import com.jimbroze.kbus.core.fixtures.TestPublisherFactories
import com.jimbroze.kbus.core.fixtures.TestTransactionManager
import com.jimbroze.kbus.core.fixtures.TestUnitOfWorkFactory
import com.jimbroze.kbus.core.fixtures.TransactionCommand
import com.jimbroze.kbus.core.fixtures.TransactionCommandHandler
import com.jimbroze.kbus.core.fixtures.noOutboxPublisherFactory
import com.jimbroze.kbus.core.messages.event.IntegrationEventPublisherFactory
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.core.uow.TransactionalOutbox
import com.jimbroze.kbus.core.uow.TransactionalOutboxFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CommandExecutorTest {
    @Test
    fun test_it_invokes_handler_and_returns_result() = runTest {
        val factories = TestPublisherFactories()
        val executor =
            CommandExecutor(
                null,
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                factories.invocationFactory,
            )

        val result = executor.execute(ReturnCommand("Wassup")) { ReturnCommandHandler() }

        assertEquals(BusResult.success("Wassup"), result)
    }

    @Test
    fun test_it_gives_handlers_access_to_bus() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val factories = TestPublisherFactories(basePublisher)
        val executor =
            CommandExecutor(
                null,
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                factories.invocationFactory,
            )

        val handler = DispatchingCommandHandler()

        executor.execute(DispatchingCommand()) { handler }

        assertEquals(1, basePublisher.publishedEvents.flatten().size)
        assertEquals(
            "test-event",
            (basePublisher.publishedEvents.flatten()[0] as TestIntegrationEvent).name,
        )
    }

    @Test
    fun test_it_routes_dispatch_through_the_invocations_outbox_when_present() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val factories = TestPublisherFactories(basePublisher)
        val store = RecordingOutboxStore()
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        val invocationFactory =
            CommandInvocationFactory(
                unitOfWorkFactory,
                IntegrationEventPublisherFactory(
                    TransactionalOutboxFactory(
                        OutboxConfig(store),
                        RecordingIntegrationEventPublisher(),
                        this,
                    ),
                    basePublisher,
                ),
            )
        val executor =
            CommandExecutor(
                null,
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                invocationFactory,
            )

        executor.execute(DispatchingCommand()) { DispatchingCommandHandler() }
        unitOfWorkFactory.unitOfWork.executeAllScheduledWork()

        assertTrue(basePublisher.publishedEvents.isEmpty())
        assertEquals(1, store.saved.size)
        assertEquals("test-event", (store.saved.single().event as TestIntegrationEvent).name)
    }

    @Test
    fun test_the_commands_middleware_context_resolves_to_the_invocations_outbox() = runTest {
        val basePublisher = RecordingIntegrationEventPublisher()
        val factories = TestPublisherFactories(basePublisher)
        val store = RecordingOutboxStore()
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        val invocationFactory =
            CommandInvocationFactory(
                unitOfWorkFactory,
                IntegrationEventPublisherFactory(
                    TransactionalOutboxFactory(
                        OutboxConfig(store),
                        RecordingIntegrationEventPublisher(),
                        this,
                    ),
                    basePublisher,
                ),
            )
        val capturingMiddleware = CapturingContextMiddleware()
        val executor =
            CommandExecutor(
                null,
                listOf(capturingMiddleware),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                invocationFactory,
            )

        executor.execute(ReturnCommand("test")) { ReturnCommandHandler() }

        assertIs<TransactionalOutbox>(
            capturingMiddleware.capturedContext?.integrationEventPublisher
        )
    }

    @Test
    fun test_it_executes_handler_in_unit_of_work() = runTest {
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        val factories = TestPublisherFactories()
        val invocationFactory =
            CommandInvocationFactory(unitOfWorkFactory, noOutboxPublisherFactory())
        val executor =
            CommandExecutor(
                null,
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                invocationFactory,
            )

        executor.execute(ReturnCommand("Primary")) { ReturnCommandHandler() }

        val unitOfWork = unitOfWorkFactory.unitOfWork
        assertEquals(1, unitOfWork.executedWork.size)
        assertEquals(BusResult.success<Any>("Primary"), unitOfWork.execute())
    }

    @Test
    fun test_it_uses_provided_transaction_manager() = runTest {
        val testTransactionManager = TestTransactionManager()
        val factories = TestPublisherFactories()
        val executor =
            CommandExecutor(
                testTransactionManager,
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                factories.invocationFactory,
            )

        executor.execute(TransactionCommand("Transaction")) { TransactionCommandHandler() }

        assertContentEquals(
            listOf(BusResult.success("Transaction")),
            testTransactionManager.executedWork,
        )
    }

    @Test
    fun test_it_errors_for_executeInTransaction_if_no_transaction_manager() = runTest {
        val factories = TestPublisherFactories()
        val executor =
            CommandExecutor(
                null,
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                factories.invocationFactory,
            )

        assertFailsWith<IllegalStateException> {
            executor.execute(TransactionCommand("Transaction")) { TransactionCommandHandler() }
        }
    }

    @Test
    fun test_it_uses_handler_transaction_manager_when_provided() = runTest {
        val defaultTransactionManager = TestTransactionManager()
        val handlerTransactionManager = TestTransactionManager()
        val factories = TestPublisherFactories()
        val executor =
            CommandExecutor(
                defaultTransactionManager,
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                factories.invocationFactory,
            )

        val command = TransactionCommand("HandlerTransaction")

        executor.execute(command) { TransactionCommandHandler(handlerTransactionManager) }

        assertEquals(0, defaultTransactionManager.executedWork.size)
        assertContentEquals(
            listOf(BusResult.success("HandlerTransaction")),
            handlerTransactionManager.executedWork,
        )
    }

    @Test
    fun test_default_transaction_manager_can_be_null_if_handler_manager_is_provided() = runTest {
        val handlerTransactionManager = TestTransactionManager()
        val factories = TestPublisherFactories()
        val executor =
            CommandExecutor(
                null,
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                factories.invocationFactory,
            )

        val command = TransactionCommand("Transaction")

        executor.execute(command) { TransactionCommandHandler(handlerTransactionManager) }

        assertContentEquals(
            listOf(BusResult.success("Transaction")),
            handlerTransactionManager.executedWork,
        )
    }

    @Test
    fun test_it_passes_command_dependencies_to_handler() = runTest {
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        var testDependencies: CommandDependencies? = null
        val dependenciesFactory = TestCommandDependenciesFactory()
        val factories = TestPublisherFactories()
        val invocationFactory =
            CommandInvocationFactory(unitOfWorkFactory, noOutboxPublisherFactory())
        val executor =
            CommandExecutor(
                null,
                emptyList(),
                factories.contextFactory,
                dependenciesFactory,
                invocationFactory,
            )
        val createHandler: (CommandDependencies) -> ReturnCommandHandler = { commandDependencies ->
            testDependencies = commandDependencies
            ReturnCommandHandler()
        }

        executor.execute(ReturnCommand("Primary"), createHandler)

        val unitOfWork = unitOfWorkFactory.unitOfWork
        assertNotNull(testDependencies)
        assertSame(testDependencies, dependenciesFactory.commandDependencies)
        assertSame(unitOfWork, dependenciesFactory.unitOfWork)
    }
}
