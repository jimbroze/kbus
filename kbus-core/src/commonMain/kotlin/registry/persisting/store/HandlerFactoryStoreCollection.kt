package com.jimbroze.kbus.core.registry.persisting.store

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.event.Event
import com.jimbroze.kbus.api.messages.query.Query

data class HandlerFactoryStoreCollection(
    val commandStore: MessageHandlerFactoryStore<Command<*>> = MessageHandlerFactoryStore(),
    val queryStore: MessageHandlerFactoryStore<Query<*>> = MessageHandlerFactoryStore(),
    val eventStore: MessageHandlerFactoryStore<Event> = MessageHandlerFactoryStore(),
)
