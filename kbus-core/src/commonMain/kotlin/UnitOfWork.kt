package com.jimbroze.kbus.core

import com.jimbroze.kbus.core.domain.DomainEvent
import com.jimbroze.kbus.core.domain.DomainEventPublisher

interface UnitOfWork {
    suspend fun execute(): Any?

    fun addSecondaryWork(subUnitOfWork: suspend () -> Unit)

    fun addPostCommitWork(subUnitOfWork: suspend () -> Unit)

    fun setReturningWork(primaryWork: suspend () -> Any?)

    fun useTransaction(transactionManager: TransactionManager)
}

internal class UnitOfWorkImpl internal constructor() : UnitOfWork {
    private var primaryWork: suspend () -> Any? = {}
    private val secondaryWork: MutableList<suspend () -> Unit> = mutableListOf()
    private val postCommitWork: MutableList<suspend () -> Unit> = mutableListOf()
    private var transactionManager: TransactionManager = EmptyTransactionManager()

    override suspend fun execute(): Any? {
        val blockForTransaction: suspend () -> Any? = {
            val result = primaryWork()
            executeSecondaryWork()
            result
        }

        val transactionManager = this.transactionManager
        val result = transactionManager.execute(blockForTransaction)

        executeAfterCommitWork()

        return result
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
    val unitOfWork: UnitOfWork,
) : DomainEventPublisher {
    override suspend fun dispatch(event: DomainEvent) {
        baseDispatcher?.dispatch(event, unitOfWork)
    }
}

interface UnitOfWorkFactory {
    fun create(): UnitOfWork
}

class DefaultUnitOfWorkFactory : UnitOfWorkFactory {
    override fun create(): UnitOfWork = UnitOfWorkImpl()
}

interface ExecuteInTransaction<TCommand : Command> : MessageHandler<TCommand> {
    val transactionManager: TransactionManager?
        get() = null
}

interface TransactionManager {
    suspend fun execute(block: suspend () -> Any?): Any?
}

class EmptyTransactionManager : TransactionManager {
    override suspend fun execute(block: suspend () -> Any?): Any? = block()
}
