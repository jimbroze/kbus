package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.contracts.result.ResultReturningMessageHandler
import com.jimbroze.kbus.core.messages.event.DomainEventDispatcher
import com.jimbroze.kbus.domain.DomainEvent
import com.jimbroze.kbus.domain.DomainEventPublisher

interface UnitOfWork<TResult> {
    suspend fun execute(): TResult

    fun addSecondaryWork(subUnitOfWork: suspend () -> Unit)

    fun addPostCommitWork(subUnitOfWork: suspend () -> Unit)

    fun setReturningWork(primaryWork: suspend () -> TResult)

    fun useTransaction(transactionManager: TransactionManager)
}

internal class UnitOfWorkImpl<TResult> internal constructor() : UnitOfWork<TResult> {
    private lateinit var primaryWork: suspend () -> TResult
    private val secondaryWork: MutableList<suspend () -> Unit> = mutableListOf()
    private val postCommitWork: MutableList<suspend () -> Unit> = mutableListOf()
    private var transactionManager: TransactionManager = EmptyTransactionManager()

    override suspend fun execute(): TResult {
        val blockForTransaction: suspend () -> TResult = {
            val result = primaryWork()
            executeSecondaryWork()
            result
        }

        val transactionManager = this.transactionManager
        val result = transactionManager.execute(blockForTransaction)

        executeAfterCommitWork()

        return result
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

    private suspend fun executeSecondaryWork() {
        secondaryWork.forEach { it() }
    }

    private suspend fun executeAfterCommitWork() {
        postCommitWork.forEach { it() }
    }
}

data class CommandDependencies(val domainEventPublisher: DomainEventPublisher)

class UnitOfWorkDomainEventPublisher(
    val baseDispatcher: DomainEventDispatcher?,
    val unitOfWork: UnitOfWork<*>,
) : DomainEventPublisher {
    override suspend fun dispatch(event: DomainEvent) {
        baseDispatcher?.dispatch(event, unitOfWork)
    }
}

interface UnitOfWorkFactory {
    fun <TResult> create(): UnitOfWork<TResult>
}

class DefaultUnitOfWorkFactory : UnitOfWorkFactory {
    override fun <TResult> create(): UnitOfWork<TResult> = UnitOfWorkImpl()
}

interface ExecuteInTransaction<TCommand : Command<TResult>, TResult : KBusResult> :
    ResultReturningMessageHandler<TCommand, TResult> {
    val transactionManager: TransactionManager?
        get() = null
}

interface TransactionManager {
    suspend fun <TResult> execute(block: suspend () -> TResult): TResult
}

class EmptyTransactionManager : TransactionManager {
    override suspend fun <TResult> execute(block: suspend () -> TResult): TResult = block()
}
