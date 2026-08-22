package com.jimbroze.kbus.infrastructure.transaction.adapters

import com.jimbroze.kbus.infrastructure.transaction.TransactionManager

class EmptyTransactionManager : TransactionManager {
    override suspend fun <TResult> execute(block: suspend () -> TResult): TResult = block()
}
