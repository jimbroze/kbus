package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.core.messages.command.CommandInvocation
import com.jimbroze.kbus.core.messages.event.dispatch.DomainEventDispatcher
import com.jimbroze.kbus.core.uow.UnitOfWork
import com.jimbroze.kbus.core.uow.UnitOfWorkFactory
import com.jimbroze.kbus.domain.event.DomainEvent

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
    var transactionManager: TransactionManager? = null

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
        this.transactionManager = transactionManager
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
