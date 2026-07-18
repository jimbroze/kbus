package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.core.messages.event.IntegrationEventPublisher

interface UnitOfWork<TResult> {
    /** Routes publishes through the transactional outbox when one is configured. */
    val integrationEventPublisher: IntegrationEventPublisher

    suspend fun execute(): TResult

    fun addSecondaryWork(subUnitOfWork: suspend () -> Unit)

    fun addPostCommitWork(subUnitOfWork: suspend () -> Unit)

    fun setReturningWork(primaryWork: suspend () -> TResult)

    fun useTransaction(transactionManager: TransactionManager)
}

interface UnitOfWorkFactory {
    fun <TResult> create(): UnitOfWork<TResult>
}
