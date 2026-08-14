package com.jimbroze.kbus.example.fixtures

import com.jimbroze.kbus.infrastructure.transaction.TransactionManager

val myTransactionManager =
    object : TransactionManager {
        override suspend fun <TResult> execute(block: suspend () -> TResult): TResult = block()
    }
