// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleUnitOfWork01

import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.registry.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.PersistingHandlerLocator
import com.jimbroze.kbus.example.fixtures.myTransactionManager

val stores = HandlerFactoryStoreCollection()

val bus = MessageBus(
    handlerLocator = PersistingHandlerLocator(stores),
    transactionManager = myTransactionManager,
)
