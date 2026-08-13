package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.core.fixtures.CapturingContextMiddleware
import com.jimbroze.kbus.core.fixtures.DispatchingCommand
import com.jimbroze.kbus.core.fixtures.DispatchingCommandHandler
import com.jimbroze.kbus.core.fixtures.RecordingDestination
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.fixtures.ReturnCommand
import com.jimbroze.kbus.core.fixtures.ReturnCommandHandler
import com.jimbroze.kbus.core.fixtures.TestCommandDependenciesFactory
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import com.jimbroze.kbus.core.fixtures.TestOwningContext
import com.jimbroze.kbus.core.fixtures.TestPublisherFactories
import com.jimbroze.kbus.core.fixtures.TestTransactionManager
import com.jimbroze.kbus.core.fixtures.TestUnitOfWorkFactory
import com.jimbroze.kbus.core.fixtures.TransactionCommand
import com.jimbroze.kbus.core.fixtures.TransactionCommandHandler
import com.jimbroze.kbus.core.fixtures.noOutboxPublisherFactory
import com.jimbroze.kbus.core.messages.event.publish.DirectPublisher
import com.jimbroze.kbus.core.messages.event.publish.IntegrationEventPublisherFactory
import com.jimbroze.kbus.core.messages.event.routing.EventRouter
import com.jimbroze.kbus.core.uow.EmptyTransactionManager
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.core.uow.OutboxCoordinator
import com.jimbroze.kbus.core.uow.TransactionalOutbox
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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
    fun `returns what the command's handler returned`() = runTest {
        val factories = TestPublisherFactories(backgroundScope)
        val executor =
            CommandExecutor(
                EmptyTransactionManager(),
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                factories.invocationFactory,
            )

        val result =
            executor.execute(ReturnCommand("Wassup"), TestOwningContext()) { _, _ ->
                ReturnCommandHandler()
            }

        assertEquals(BusResult.success("Wassup"), result)
    }

    @Test
    fun `builds a handler with the publisher its own invocation carries`() = runTest {
        val destination = RecordingDestination()
        val factories =
            TestPublisherFactories(
                backgroundScope,
                DirectPublisher(EventRouter(listOf(destination)), this),
            )
        val executor =
            CommandExecutor(
                EmptyTransactionManager(),
                emptyList(),
                factories.contextFactory,
                DefaultCommandDependenciesFactory(),
                factories.invocationFactory,
            )

        executor.execute(DispatchingCommand(), TestOwningContext()) { commandDependencies, _ ->
            DispatchingCommandHandler(commandDependencies.integrationEventPublisher)
        }
        advanceUntilIdle()

        assertEquals(
            "test-event",
            (destination.delivered.single().event as TestIntegrationEvent).name,
        )
    }

    @Test
    fun `gives a handler the bus it can send further messages through`() = runTest {
        val destination = RecordingDestination()
        val factories =
            TestPublisherFactories(
                backgroundScope,
                DirectPublisher(EventRouter(listOf(destination)), this),
            )
        val executor =
            CommandExecutor(
                EmptyTransactionManager(),
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                factories.invocationFactory,
            )

        executor.execute(DispatchingCommand(), TestOwningContext()) { commandDependencies, _ ->
            DispatchingCommandHandler(commandDependencies.integrationEventPublisher)
        }
        advanceUntilIdle()

        assertEquals(1, destination.delivered.size)
        assertEquals("test-event", (destination.delivered[0].event as TestIntegrationEvent).name)
    }

    @Test
    fun `routes event dispatch through the invocation's outbox when it has one`() = runTest {
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
                EmptyTransactionManager(),
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                invocationFactory,
            )

        executor.execute(DispatchingCommand(), TestOwningContext()) { commandDependencies, _ ->
            DispatchingCommandHandler(commandDependencies.integrationEventPublisher)
        }
        unitOfWorkFactory.unitOfWork.executeAllScheduledWork()

        assertTrue(directDestination.delivered.isEmpty())
        assertEquals(1, store.saved.size)
        assertEquals("test-event", (store.saved.single().event as TestIntegrationEvent).name)
    }

    @Test
    fun `gives the command's middleware the outbox its invocation publishes through`() = runTest {
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
                EmptyTransactionManager(),
                listOf(capturingMiddleware),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                invocationFactory,
            )

        executor.execute(ReturnCommand("test"), TestOwningContext()) { _, _ ->
            ReturnCommandHandler()
        }

        assertIs<TransactionalOutbox>(
            capturingMiddleware.capturedContext?.integrationEventPublisher
        )
    }

    @Test
    fun `runs the handler inside a unit of work`() = runTest {
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        val factories = TestPublisherFactories(backgroundScope)
        val invocationFactory =
            CommandInvocationFactory(unitOfWorkFactory, noOutboxPublisherFactory(backgroundScope))
        val executor =
            CommandExecutor(
                EmptyTransactionManager(),
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                invocationFactory,
            )

        executor.execute(ReturnCommand("Primary"), TestOwningContext()) { _, _ ->
            ReturnCommandHandler()
        }

        val unitOfWork = unitOfWorkFactory.unitOfWork
        assertEquals(1, unitOfWork.executedWork.size)
        assertEquals(BusResult.success<Any>("Primary"), unitOfWork.execute())
    }

    @Test
    fun `runs the unit of work under the transaction manager it was given`() = runTest {
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

        executor.execute(TransactionCommand("Transaction"), TestOwningContext()) { _, _ ->
            TransactionCommandHandler()
        }

        assertContentEquals(
            listOf(BusResult.success("Transaction")),
            testTransactionManager.executedWork,
        )
    }

    @Test
    fun `prefers the transaction manager the handler declares`() = runTest {
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

        executor.execute(command, TestOwningContext()) { _, _ ->
            TransactionCommandHandler(handlerTransactionManager)
        }

        assertEquals(0, defaultTransactionManager.executedWork.size)
        assertContentEquals(
            listOf(BusResult.success("HandlerTransaction")),
            handlerTransactionManager.executedWork,
        )
    }

    @Test
    fun `uses the transaction manager the handler declares when it has no default`() = runTest {
        val handlerTransactionManager = TestTransactionManager()
        val factories = TestPublisherFactories(backgroundScope)
        val executor =
            CommandExecutor(
                EmptyTransactionManager(),
                emptyList(),
                factories.contextFactory,
                TestCommandDependenciesFactory(),
                factories.invocationFactory,
            )

        val command = TransactionCommand("Transaction")

        executor.execute(command, TestOwningContext()) { _, _ ->
            TransactionCommandHandler(handlerTransactionManager)
        }

        assertContentEquals(
            listOf(BusResult.success("Transaction")),
            handlerTransactionManager.executedWork,
        )
    }

    @Test
    fun `passes the command's declared dependencies to its handler`() = runTest {
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        var testDependencies: CommandDependencies? = null
        val dependenciesFactory = TestCommandDependenciesFactory()
        val factories = TestPublisherFactories(backgroundScope)
        val invocationFactory =
            CommandInvocationFactory(unitOfWorkFactory, noOutboxPublisherFactory(backgroundScope))
        val executor =
            CommandExecutor(
                EmptyTransactionManager(),
                emptyList(),
                factories.contextFactory,
                dependenciesFactory,
                invocationFactory,
            )
        val createHandler: (CommandDependencies, NestedCommandExecutor) -> ReturnCommandHandler =
            { commandDependencies, _ ->
                testDependencies = commandDependencies
                ReturnCommandHandler()
            }

        executor.execute(ReturnCommand("Primary"), TestOwningContext(), createHandler)

        val unitOfWork = unitOfWorkFactory.unitOfWork
        assertNotNull(testDependencies)
        assertSame(testDependencies, dependenciesFactory.commandDependencies)
        assertSame(unitOfWork, dependenciesFactory.unitOfWork)
    }

    @Test
    fun `builds a handler with the same nested executor its dependencies carry`() = runTest {
        val owningContext = TestOwningContext()
        var passedCommands: NestedCommandExecutor? = null
        var passedDependencies: CommandDependencies? = null
        val executor =
            CommandExecutor(
                EmptyTransactionManager(),
                emptyList(),
                TestPublisherFactories(backgroundScope).contextFactory,
                DefaultCommandDependenciesFactory(),
                CommandInvocationFactory(
                    TestUnitOfWorkFactory(),
                    noOutboxPublisherFactory(backgroundScope),
                ),
            )

        executor.execute(ReturnCommand("Primary"), owningContext) { dependencies, commands ->
            passedDependencies = dependencies
            passedCommands = commands
            ReturnCommandHandler()
        }

        assertEquals(1, owningContext.typedCommandsPassed.size)
        assertSame(owningContext.typedCommandsPassed.single(), passedCommands)
        assertSame(passedDependencies!!.commandExecutor, passedCommands)
    }
}
