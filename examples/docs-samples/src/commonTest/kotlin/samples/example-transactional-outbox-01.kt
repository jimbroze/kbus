// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleTransactionalOutbox01

import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.infrastructure.outbox.adapters.InMemoryOutboxStore
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.uow.OutboxConfig

val stores = HandlerFactoryStoreCollection()

val bus = MessageBus(
    handlerLocator = PersistingHandlerLocator(stores),
    outbox = OutboxConfig(store = InMemoryOutboxStore()),
).apply { start() }
