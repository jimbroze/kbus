// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.examplePerContextInbox01

import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.infrastructure.outbox.InMemoryOutboxStore
import com.jimbroze.kbus.core.infrastructure.inbox.InMemoryInboxStore
import com.jimbroze.kbus.core.module.BoundedContext
import com.jimbroze.kbus.core.module.BoundedContextId
import com.jimbroze.kbus.core.module.inbox.ContextInbox
import com.jimbroze.kbus.core.module.inbox.InboxAckPolicy
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.uow.OutboxConfig

val stores = HandlerFactoryStoreCollection()
val ordersLocator = PersistingHandlerLocator(stores)
val inventoryLocator = PersistingHandlerLocator(stores)

val bus = MessageBus(
    handlerLocator = PersistingHandlerLocator(stores),
    contexts = listOf(
        BoundedContext(
            BoundedContextId("orders"),
            ordersLocator,
            inbox = ContextInbox(InMemoryInboxStore(), InboxAckPolicy.HonourEventStrategy),
        ),
        BoundedContext(
            BoundedContextId("inventory"),
            inventoryLocator,
            inbox = ContextInbox(InMemoryInboxStore(), InboxAckPolicy.HonourEventStrategy),
        ),
    ),
    outbox = OutboxConfig(store = InMemoryOutboxStore()),
).apply { start() }
