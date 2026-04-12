package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.core.fixtures.DispatchingCommand
import com.jimbroze.kbus.core.fixtures.DispatchingCommandHandler
import com.jimbroze.kbus.core.fixtures.ReturnCommand
import com.jimbroze.kbus.core.fixtures.ReturnCommandHandler
import com.jimbroze.kbus.core.fixtures.TestBusAccess
import com.jimbroze.kbus.core.fixtures.TestCommandDependenciesFactory
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import com.jimbroze.kbus.core.fixtures.TestTransactionManager
import com.jimbroze.kbus.core.fixtures.TestUnitOfWorkFactory
import com.jimbroze.kbus.core.fixtures.TransactionCommand
import com.jimbroze.kbus.core.fixtures.TransactionCommandHandler
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

class CommandExecutorTest {
    @Test
    fun test_it_invokes_handler_and_returns_result() = runTest {
        val executor =
            CommandExecutor(null, emptyList(), TestBusAccess(), TestCommandDependenciesFactory())

        val result = executor.execute(ReturnCommand("Wassup")) { ReturnCommandHandler() }

        assertEquals(BusResult.success("Wassup"), result)
    }

    @Test
    fun test_it_gives_handlers_access_to_bus() = runTest {
        val busAccess = TestBusAccess()
        val executor =
            CommandExecutor(null, emptyList(), busAccess, TestCommandDependenciesFactory())

        val handler = DispatchingCommandHandler()

        executor.execute(DispatchingCommand()) { handler }

        assertEquals(1, busAccess.dispatchedEvents.size)
        assertEquals("test-event", (busAccess.dispatchedEvents[0] as TestIntegrationEvent).name)
    }

    @Test
    fun test_it_executes_handler_in_unit_of_work() = runTest {
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        val executor =
            CommandExecutor(
                null,
                emptyList(),
                TestBusAccess(),
                TestCommandDependenciesFactory(),
                unitOfWorkFactory,
            )

        executor.execute(ReturnCommand("Primary")) { ReturnCommandHandler() }

        val unitOfWork = unitOfWorkFactory.unitOfWork
        assertEquals(1, unitOfWork.executedWork.size)
        assertEquals(BusResult.success<Any>("Primary"), unitOfWork.execute())
    }

    @Test
    fun test_it_uses_provided_transaction_manager() = runTest {
        val testTransactionManager = TestTransactionManager()
        val executor =
            CommandExecutor(
                testTransactionManager,
                emptyList(),
                TestBusAccess(),
                TestCommandDependenciesFactory(),
            )

        executor.execute(TransactionCommand("Transaction")) { TransactionCommandHandler() }

        assertContentEquals(
            listOf(BusResult.success("Transaction")),
            testTransactionManager.executedWork,
        )
    }

    @Test
    fun test_it_errors_for_executeInTransaction_if_no_transaction_manager() = runTest {
        val executor =
            CommandExecutor(null, emptyList(), TestBusAccess(), TestCommandDependenciesFactory())

        assertFailsWith<IllegalStateException> {
            executor.execute(TransactionCommand("Transaction")) { TransactionCommandHandler() }
        }
    }

    @Test
    fun test_it_uses_handler_transaction_manager_when_provided() = runTest {
        val defaultTransactionManager = TestTransactionManager()
        val handlerTransactionManager = TestTransactionManager()
        val executor =
            CommandExecutor(
                defaultTransactionManager,
                emptyList(),
                TestBusAccess(),
                TestCommandDependenciesFactory(),
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
        val executor =
            CommandExecutor(null, emptyList(), TestBusAccess(), TestCommandDependenciesFactory())

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
        val executor =
            CommandExecutor(
                null,
                emptyList(),
                TestBusAccess(),
                dependenciesFactory,
                unitOfWorkFactory,
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
