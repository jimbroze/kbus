package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.core.messages.event.IntegrationEventPublisher

internal class DefaultUnitOfWork<TResult>
internal constructor(private val outbox: TransactionOutbox? = null) : UnitOfWork<TResult> {
    private lateinit var primaryWork: suspend () -> TResult
    private val secondaryWork: MutableList<suspend () -> Unit> = mutableListOf()
    private val postCommitWork: MutableList<suspend () -> Unit> = mutableListOf()
    private var transactionManager: TransactionManager = EmptyTransactionManager()

    override val integrationEventPublisher: IntegrationEventPublisher? = outbox

    override suspend fun execute(): TResult {
        val blockForTransaction: suspend () -> TResult = {
            val result = primaryWork()
            executeSecondaryWork()
            result
        }

        val transactionManager = this.transactionManager
        val result = transactionManager.execute(blockForTransaction)

        executeAfterCommitWork()
        outbox?.drain()

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

class DefaultUnitOfWorkFactory
internal constructor(private val outboxFactory: (() -> TransactionOutbox)? = null) :
    UnitOfWorkFactory {
    constructor() : this(outboxFactory = null)

    override fun <TResult> create(): UnitOfWork<TResult> =
        DefaultUnitOfWork(outboxFactory?.invoke())
}

class EmptyTransactionManager : TransactionManager {
    override suspend fun <TResult> execute(block: suspend () -> TResult): TResult = block()
}
