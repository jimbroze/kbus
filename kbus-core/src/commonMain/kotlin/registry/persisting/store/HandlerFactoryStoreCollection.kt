package com.jimbroze.kbus.core.registry.persisting.store

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.query.Query

data class HandlerFactoryStoreCollection(
    val commandStore: MessageHandlerFactoryStore<Command<*>> = MessageHandlerFactoryStore(),
    val queryStore: MessageHandlerFactoryStore<Query<*>> = MessageHandlerFactoryStore(),
    val eventStore: MessageHandlerFactoryStore<Event> = MessageHandlerFactoryStore(),
)
