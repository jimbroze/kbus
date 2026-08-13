package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.api.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.api.result.KBusResult
import com.jimbroze.kbus.api.uow.TransactionManager
import com.jimbroze.kbus.core.boundedcontext.CommandOwningContext
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.messages.command.CommandInvocation
import com.jimbroze.kbus.core.messages.command.NestedCommandExecutor
import com.jimbroze.kbus.core.messages.event.dispatch.DomainEventDispatcher
import com.jimbroze.kbus.core.uow.UnitOfWork
import com.jimbroze.kbus.core.uow.UnitOfWorkFactory
import com.jimbroze.kbus.domain.event.DomainEvent
import kotlin.reflect.KClass

class TestTransactionManager : TransactionManager {
    val executedWork = mutableListOf<Any?>()

    override suspend fun <TResult> execute(block: suspend () -> TResult): TResult {
        val result = block()
        executedWork.add(result)

        return result
    }
}

class NonExecutingTransactionManager : TransactionManager {
    override suspend fun <TResult> execute(block: suspend () -> TResult): TResult {
        @Suppress("UNCHECKED_CAST")
        return null as TResult
    }
}

class TestUnitOfWorkFactory : UnitOfWorkFactory {
    lateinit var unitOfWork: TestUnitOfWork<*>

    override fun <TResult> create(): UnitOfWork<TResult> {
        val unitOfWork = TestUnitOfWork<TResult>()
        this.unitOfWork = unitOfWork
        return unitOfWork
    }
}

class TestUnitOfWork<TResult> : UnitOfWork<TResult> {
    lateinit var primaryWork: suspend () -> TResult
    val secondaryWork = mutableListOf<suspend () -> Unit>()
    val postCommitWork = mutableListOf<suspend () -> Unit>()
    val executedWork = mutableListOf<suspend () -> Any?>()
    override var activeTransactionManager: TransactionManager? = null

    override suspend fun execute(): TResult {
        executedWork.add(primaryWork)

        return primaryWork()
    }

    override fun setReturningWork(primaryWork: suspend () -> TResult) {
        this.primaryWork = primaryWork
    }

    override fun addSecondaryWork(subUnitOfWork: suspend () -> Unit) {
        secondaryWork.add(subUnitOfWork)
    }

    override fun addPostCommitWork(subUnitOfWork: suspend () -> Unit) {
        postCommitWork.add(subUnitOfWork)
    }

    override fun useTransaction(transactionManager: TransactionManager) {
        this.activeTransactionManager = transactionManager
    }

    suspend fun executeAllScheduledWork() {
        secondaryWork.forEach { it.invoke() }
        postCommitWork.forEach { it.invoke() }
    }
}

/** Builds a [CommandInvocation] for tests, defaulting to a fresh [TestUnitOfWork]. */
fun <TResult> testInvocation(
    unitOfWork: UnitOfWork<TResult> = TestUnitOfWork(),
    publisher: IntegrationEventPublisher = EmptyIntegrationEventPublisher,
): CommandInvocation<TResult> = CommandInvocation(unitOfWork, publisher)

class TestDomainEventDispatcher : DomainEventDispatcher {
    val dispatchedEvents = mutableListOf<Pair<DomainEvent, CommandInvocation<*>>>()

    override suspend fun <TEvent : DomainEvent> dispatchDomainEvent(
        event: TEvent,
        invocation: CommandInvocation<*>,
    ) {
        dispatchedEvents.add(Pair(event, invocation))
    }
}

/**
 * A [CommandOwningContext] whose handlers are supplied per command class, for tests that execute a
 * command without a bus. Owns nothing by default.
 */
class TestOwningContext(
    override val domainEventDispatcher: TestDomainEventDispatcher = TestDomainEventDispatcher(),
    private val handlers:
        Map<KClass<out Command<*>>, (CommandDependencies) -> CommandHandler<*, *>> =
        emptyMap(),
) : CommandOwningContext<NestedCommandExecutor> {
    val dependenciesPassed = mutableListOf<CommandDependencies>()

    val typedCommandsPassed = mutableListOf<NestedCommandExecutor>()

    override fun typedCommands(
        nestedCommandExecutor: NestedCommandExecutor
    ): NestedCommandExecutor {
        typedCommandsPassed.add(nestedCommandExecutor)
        return nestedCommandExecutor
    }

    override fun <TCommand : Command<TResult>, TResult : KBusResult> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TResult>? {
        dependenciesPassed.add(commandDependencies)

        @Suppress("UNCHECKED_CAST")
        return handlers[command::class]?.invoke(commandDependencies)
            as CommandHandler<TCommand, TResult>?
    }
}
