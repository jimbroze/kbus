package com.jimbroze.kbus.contracts.uow

interface TransactionManager {
    suspend fun <TResult> execute(block: suspend () -> TResult): TResult
}
