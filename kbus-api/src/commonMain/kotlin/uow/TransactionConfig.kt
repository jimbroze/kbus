package com.jimbroze.kbus.api.uow

data class TransactionConfig(val transactionManagerOverride: TransactionManager? = null)
