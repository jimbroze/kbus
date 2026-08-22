package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.infrastructure.transaction.TransactionManager
import com.jimbroze.kbus.infrastructure.transaction.adapters.EmptyTransactionManager

internal class DefaultUnitOfWork<TResult> internal constructor() : UnitOfWork<TResult> {
    private lateinit var primaryWork: suspend () -> TResult
    private val secondaryWork: MutableList<suspend () -> Unit> = mutableListOf()
    private val postCommitWork: MutableList<suspend () -> Unit> = mutableListOf()
    override var activeTransactionManager: TransactionManager? = null
        private set

    override suspend fun execute(): TResult {
        val blockForTransaction: suspend () -> TResult = {
            val result = primaryWork()
            executeSecondaryWork()
            result
        }

        val transactionManager = activeTransactionManager ?: EmptyTransactionManager()
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
        this.activeTransactionManager = transactionManager
    }

    private suspend fun executeSecondaryWork() {
        secondaryWork.forEach { it() }
    }

    private suspend fun executeAfterCommitWork() {
        postCommitWork.forEach { it() }
    }
}

class DefaultUnitOfWorkFactory : UnitOfWorkFactory {
    override fun <TResult> create(): UnitOfWork<TResult> = DefaultUnitOfWork()
}
