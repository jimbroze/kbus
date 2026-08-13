package com.jimbroze.kbus.api.uow

interface TransactionManager {
    suspend fun <TResult> execute(block: suspend () -> TResult): TResult
}
