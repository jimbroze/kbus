package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.contracts.uow.TransactionManager

class EmptyTransactionManager : TransactionManager {
    override suspend fun <TResult> execute(block: suspend () -> TResult): TResult = block()
}
