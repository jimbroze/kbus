package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.core.fixtures.CapturingContextMiddleware
import com.jimbroze.kbus.core.fixtures.DispatchingCommand
import com.jimbroze.kbus.core.fixtures.DispatchingCommandHandler
import com.jimbroze.kbus.core.fixtures.RecordingDestination
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
import com.jimbroze.kbus.core.messages.event.publish.DirectPublisher
import com.jimbroze.kbus.core.messages.event.publish.IntegrationEventPublisherFactory
import com.jimbroze.kbus.core.messages.event.routing.EventRouter
import com.jimbroze.kbus.core.module.BoundedContextId
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.core.uow.OutboxCoordinator
import com.jimbroze.kbus.core.uow.TransactionalOutbox
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CommandExecutorTest {
    @Test
    fun test_it_invokes_handler_and_returns_result() = runTest {
        val factories = TestPublisherFactories(backgroundScope)
        val executor =
            CommandExecutor(
                null,
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                factories.invocationFactory,
            )

        val result =
            executor.execute(ReturnCommand("Wassup"), BoundedContextId.DEFAULT) {
                ReturnCommandHandler()
            }

        assertEquals(BusResult.success("Wassup"), result)
    }

    @Test
    fun test_it_gives_handlers_access_to_bus() = runTest {
        val destination = RecordingDestination()
        val factories =
            TestPublisherFactories(
                backgroundScope,
                DirectPublisher(EventRouter(listOf(destination)), this),
            )
        val executor =
            CommandExecutor(
                null,
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                factories.invocationFactory,
            )

        val handler = DispatchingCommandHandler()

        executor.execute(DispatchingCommand(), BoundedContextId.DEFAULT) { handler }
        advanceUntilIdle()

        assertEquals(1, destination.delivered.size)
        assertEquals("test-event", (destination.delivered[0].event as TestIntegrationEvent).name)
    }

    @Test
    fun test_it_routes_dispatch_through_the_invocations_outbox_when_present() = runTest {
        val directDestination = RecordingDestination()
        val basePublisher = DirectPublisher(EventRouter(listOf(directDestination)), this)
        val factories = TestPublisherFactories(backgroundScope, basePublisher)
        val store = RecordingOutboxStore()
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        val invocationFactory =
            CommandInvocationFactory(
                unitOfWorkFactory,
                IntegrationEventPublisherFactory(
                    OutboxCoordinator(
                        OutboxConfig(store),
                        EventRouter(listOf(RecordingDestination())),
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

        executor.execute(DispatchingCommand(), BoundedContextId.DEFAULT) {
            DispatchingCommandHandler()
        }
        unitOfWorkFactory.unitOfWork.executeAllScheduledWork()

        assertTrue(directDestination.delivered.isEmpty())
        assertEquals(1, store.saved.size)
        assertEquals("test-event", (store.saved.single().event as TestIntegrationEvent).name)
    }

    @Test
    fun test_the_commands_middleware_context_resolves_to_the_invocations_outbox() = runTest {
        val basePublisher = DirectPublisher(EventRouter(emptyList()), this)
        val factories = TestPublisherFactories(backgroundScope, basePublisher)
        val store = RecordingOutboxStore()
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        val invocationFactory =
            CommandInvocationFactory(
                unitOfWorkFactory,
                IntegrationEventPublisherFactory(
                    OutboxCoordinator(
                        OutboxConfig(store),
                        EventRouter(listOf(RecordingDestination())),
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

        executor.execute(ReturnCommand("test"), BoundedContextId.DEFAULT) { ReturnCommandHandler() }

        assertIs<TransactionalOutbox>(
            capturingMiddleware.capturedContext?.integrationEventPublisher
        )
    }

    @Test
    fun test_it_executes_handler_in_unit_of_work() = runTest {
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        val factories = TestPublisherFactories(backgroundScope)
        val invocationFactory =
            CommandInvocationFactory(unitOfWorkFactory, noOutboxPublisherFactory(backgroundScope))
        val executor =
            CommandExecutor(
                null,
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                invocationFactory,
            )

        executor.execute(ReturnCommand("Primary"), BoundedContextId.DEFAULT) {
            ReturnCommandHandler()
        }

        val unitOfWork = unitOfWorkFactory.unitOfWork
        assertEquals(1, unitOfWork.executedWork.size)
        assertEquals(BusResult.success<Any>("Primary"), unitOfWork.execute())
    }

    @Test
    fun test_it_uses_provided_transaction_manager() = runTest {
        val testTransactionManager = TestTransactionManager()
        val factories = TestPublisherFactories(backgroundScope)
        val executor =
            CommandExecutor(
                testTransactionManager,
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                factories.invocationFactory,
            )

        executor.execute(TransactionCommand("Transaction"), BoundedContextId.DEFAULT) {
            TransactionCommandHandler()
        }

        assertContentEquals(
            listOf(BusResult.success("Transaction")),
            testTransactionManager.executedWork,
        )
    }

    @Test
    fun test_it_errors_for_executeInTransaction_if_no_transaction_manager() = runTest {
        val factories = TestPublisherFactories(backgroundScope)
        val executor =
            CommandExecutor(
                null,
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                factories.invocationFactory,
            )

        assertFailsWith<IllegalStateException> {
            executor.execute(TransactionCommand("Transaction"), BoundedContextId.DEFAULT) {
                TransactionCommandHandler()
            }
        }
    }

    @Test
    fun test_it_uses_handler_transaction_manager_when_provided() = runTest {
        val defaultTransactionManager = TestTransactionManager()
        val handlerTransactionManager = TestTransactionManager()
        val factories = TestPublisherFactories(backgroundScope)
        val executor =
            CommandExecutor(
                defaultTransactionManager,
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                factories.invocationFactory,
            )

        val command = TransactionCommand("HandlerTransaction")

        executor.execute(command, BoundedContextId.DEFAULT) {
            TransactionCommandHandler(handlerTransactionManager)
        }

        assertEquals(0, defaultTransactionManager.executedWork.size)
        assertContentEquals(
            listOf(BusResult.success("HandlerTransaction")),
            handlerTransactionManager.executedWork,
        )
    }

    @Test
    fun test_default_transaction_manager_can_be_null_if_handler_manager_is_provided() = runTest {
        val handlerTransactionManager = TestTransactionManager()
        val factories = TestPublisherFactories(backgroundScope)
        val executor =
            CommandExecutor(
                null,
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                factories.invocationFactory,
            )

        val command = TransactionCommand("Transaction")

        executor.execute(command, BoundedContextId.DEFAULT) {
            TransactionCommandHandler(handlerTransactionManager)
        }

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
        val factories = TestPublisherFactories(backgroundScope)
        val invocationFactory =
            CommandInvocationFactory(unitOfWorkFactory, noOutboxPublisherFactory(backgroundScope))
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

        executor.execute(ReturnCommand("Primary"), BoundedContextId.DEFAULT, createHandler)

        val unitOfWork = unitOfWorkFactory.unitOfWork
        assertNotNull(testDependencies)
        assertSame(testDependencies, dependenciesFactory.commandDependencies)
        assertSame(unitOfWork, dependenciesFactory.unitOfWork)
    }
}
