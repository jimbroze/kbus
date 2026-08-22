package com.jimbroze.kbus.infrastructure.transaction

interface TransactionManager {
    suspend fun <TResult> execute(block: suspend () -> TResult): TResult
}
