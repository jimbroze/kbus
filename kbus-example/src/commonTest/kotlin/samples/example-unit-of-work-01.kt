// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleUnitOfWork01

import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.registry.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.PersistingHandlerLocator

val myTransactionManager = object : TransactionManager {
    override suspend fun <TResult> execute(block: suspend () -> TResult): TResult = block()
}

val stores = HandlerFactoryStoreCollection()

val bus = MessageBus(
    handlerLocator = PersistingHandlerLocator(stores),
    transactionManager = myTransactionManager,
)
