package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.contracts.uow.TransactionManager

interface UnitOfWork<TResult> {
    /** The command's outbox when one is configured; routes publishes through it. */
    val transactionOutbox: TransactionOutbox?
        get() = null

    suspend fun execute(): TResult

    fun addSecondaryWork(subUnitOfWork: suspend () -> Unit)

    fun addPostCommitWork(subUnitOfWork: suspend () -> Unit)

    fun setReturningWork(primaryWork: suspend () -> TResult)

    fun useTransaction(transactionManager: TransactionManager)
}

interface UnitOfWorkFactory {
    fun <TResult> create(): UnitOfWork<TResult>
}
