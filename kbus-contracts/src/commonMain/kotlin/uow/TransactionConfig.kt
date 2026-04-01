package com.jimbroze.kbus.contracts.uow

data class TransactionConfig(val transactionManagerOverride: TransactionManager? = null)
