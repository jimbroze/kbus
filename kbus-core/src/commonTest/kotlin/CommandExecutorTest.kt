package com.jimbroze.kbus.core

import com.jimbroze.kbus.core.domain.DomainEvent
import com.jimbroze.kbus.core.domain.DomainEventPublisher
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
        // TODO
    }

    @Test
    fun test_it_executes_handler_in_unit_of_work() = runTest {
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        val unitOfWork = unitOfWorkFactory.unitOfWork
        val executor =
            CommandExecutor(
                null,
                emptyList(),
                TestBusAccess(),
                TestCommandDependenciesFactory(),
                unitOfWorkFactory,
            )

        executor.execute(ReturnCommand("Primary")) { ReturnCommandHandler() }

        assertEquals(1, unitOfWork.executedWork.size)
        assertEquals(BusResult.success<String, FailureReason>("Primary"), unitOfWork.execute())
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
            listOf(BusResult.success<String, FailureReason>("Transaction")),
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
            listOf(BusResult.success<String, FailureReason>("HandlerTransaction")),
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
            listOf(BusResult.success<String, FailureReason>("Transaction")),
            handlerTransactionManager.executedWork,
        )
    }

    @Test
    fun test_it_passes_command_dependencies_to_handler() = runTest {
        val unitOfWorkFactory = TestUnitOfWorkFactory()
        val unitOfWork = unitOfWorkFactory.unitOfWork
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

        assertNotNull(testDependencies)
        assertSame(testDependencies, dependenciesFactory.commandDependencies)
        assertSame(unitOfWork, dependenciesFactory.unitOfWork)
    }
}

class TestBusAccess : BusAccess {
    override suspend fun <TEvent : Event> dispatch(event: TEvent) {
        // No-op
    }
}

class TestDomainEventDispatcher : DomainEventDispatcher {
    val dispatchedEvents = mutableListOf<Pair<DomainEvent, UnitOfWork>>()

    override suspend fun <TEvent : DomainEvent> dispatch(event: TEvent, unitOfWork: UnitOfWork) {
        dispatchedEvents.add(Pair(event, unitOfWork))
    }
}

class TestTransactionManager : TransactionManager {
    val executedWork = mutableListOf<Any?>()

    override suspend fun execute(block: suspend () -> Any?): Any? {
        executedWork.add(block())

        return block()
    }
}

class NonExecutingTransactionManager : TransactionManager {
    override suspend fun execute(block: suspend () -> Any?): Any? {
        return null
    }
}

class TestUnitOfWorkFactory : UnitOfWorkFactory {
    val unitOfWork = TestUnitOfWork()

    override fun create(): UnitOfWork {
        return unitOfWork
    }
}

class TestUnitOfWork : UnitOfWork {
    var primaryWork: suspend () -> Any? = {}
    val secondaryWork = mutableListOf<suspend () -> Unit>()
    val postCommitWork = mutableListOf<suspend () -> Unit>()
    val executedWork = mutableListOf<suspend () -> Any?>()
    var transactionManager: TransactionManager? = null

    override suspend fun execute(): Any? {
        executedWork.add(primaryWork)

        return primaryWork()
    }

    override fun setReturningWork(primaryWork: suspend () -> Any?) {
        this.primaryWork = primaryWork
    }

    override fun addSecondaryWork(subUnitOfWork: suspend () -> Unit) {
        secondaryWork.add(subUnitOfWork)
    }

    override fun addPostCommitWork(subUnitOfWork: suspend () -> Unit) {
        postCommitWork.add(subUnitOfWork)
    }

    override fun useTransaction(transactionManager: TransactionManager) {
        this.transactionManager = transactionManager
    }
}

class TestCommandDependenciesFactory : CommandDependenciesFactory {
    var unitOfWork: UnitOfWork? = null
    var commandDependencies: CommandDependencies? = null

    override fun create(unitOfWork: UnitOfWork): CommandDependencies {
        if (this.unitOfWork !== null) {
            error("Unit of work has already been set")
        }

        val commandDependencies = CommandDependencies(TestDomainEventPublisher())

        this.unitOfWork = unitOfWork
        this.commandDependencies = commandDependencies

        return commandDependencies
    }
}

fun testCommandDependencies() = TestCommandDependenciesFactory().create(TestUnitOfWork())

class TestDomainEventPublisher : DomainEventPublisher {
    val publishedEvents = mutableListOf<DomainEvent>()

    override suspend fun dispatch(event: DomainEvent) {
        publishedEvents.add(event)
    }
}

class TransactionCommand(val message: String) : Command()

class TransactionCommandHandler(override val transactionManager: TransactionManager? = null) :
    CommandHandler<TransactionCommand, String, FailureReason>(),
    ExecuteInTransaction<TransactionCommand> {

    override suspend fun handle(message: TransactionCommand): BusResult<String, FailureReason> {
        return success(message.message)
    }
}
