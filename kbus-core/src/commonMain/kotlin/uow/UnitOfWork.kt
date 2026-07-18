package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.contracts.uow.TransactionManager

/**
 * Work items within a phase (secondary, post-commit) run in registration order. Framework hooks
 * registered at creation time (e.g. by
 * [CommandInvocationFactory][com.jimbroze.kbus.core.messages.command.CommandInvocationFactory])
 * therefore run first in their phase, ahead of anything registered while handling the command.
 */
interface UnitOfWork<TResult> {
    suspend fun execute(): TResult

    fun addSecondaryWork(subUnitOfWork: suspend () -> Unit)

    fun addPostCommitWork(subUnitOfWork: suspend () -> Unit)

    fun setReturningWork(primaryWork: suspend () -> TResult)

    fun useTransaction(transactionManager: TransactionManager)
}

interface UnitOfWorkFactory {
    fun <TResult> create(): UnitOfWork<TResult>
}
