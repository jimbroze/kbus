package com.jimbroze.kbus.core

import kotlin.reflect.KClass

open class MessageBus(val middlewares: List<Middleware> = emptyList()) {
    private val commandStore: MessageStore<Command> = MessageStore()
    private val queryStore: MessageStore<Query> = MessageStore()
    private val eventStore: MessageStore<Event> = MessageStore()

//    suspend fun <TCommand : Command> execute(command: TCommand): Any? {
//        ensureCommandHandlerExists(command::class)
//
//        val commandBus = getBus(commandStore, emptyList())
//        return commandBus(command)
//    }

    suspend fun <TCommand : Command, TReturn : Any?> execute(
        command: TCommand,
        handler: CommandHandler<TCommand, TReturn>,
    ): TReturn {
//        ensureNoOtherCommandHandlers(command::class)

        val commandBus = getBus(commandStore, listOfNotNull(handler))
        @Suppress("UNCHECKED_CAST")
        return commandBus(command) as TReturn
    }

    suspend fun <TQuery : Query, TReturn : Any> execute(
        query: TQuery,
        handler: QueryHandler<TQuery, TReturn>,
    ): Result<TReturn> {
        val queryBus = getBus(queryStore, listOfNotNull(handler))
        @Suppress("UNCHECKED_CAST")
        return queryBus(query) as Result<TReturn>
    }

    suspend fun <TEvent : Event> dispatch(
        event: TEvent,
        handlers: List<EventHandler<TEvent>> = emptyList(),
    ) {
        val eventBus = getBus(eventStore, handlers)
        eventBus(event)
    }

//    fun <TCommand : Command, TReturn : Any?> register(
//        messageType: KClass<TCommand>,
//        handler: CommandHandler<TCommand, TReturn>,
//    ) {
//        ensureNoOtherCommandHandlers(messageType)
//
//        this.commandStore.registerHandlers(messageType, listOfNotNull(handler))
//    }

    fun <TEvent : Event> register(
        eventType: KClass<TEvent>,
        handlers: List<EventHandler<TEvent>>,
    ) {
        this.eventStore.registerHandlers(eventType, handlers)
    }

//    fun <TCommand : Command> deregister(commandType: KClass<TCommand>) {
//        this.commandStore.removeHandlers(commandType)
//    }

    fun <TEvent : Event> deregister(
        messageType: KClass<TEvent>,
        handlers: List<EventHandler<TEvent>> = emptyList(),
    ) {
        this.eventStore.removeHandlers(messageType, handlers)
    }

//    fun <TCommand : Command> isRegistered(commandType: KClass<TCommand>): Boolean {
//        return this.commandStore.isRegistered(commandType)
//    }

    fun <TEvent : Event> hasHandlers(eventType: KClass<TEvent>): Int {
        return this.eventStore.getHandlers(eventType).size
    }

//    private fun <TCommand : Command> ensureCommandHandlerExists(commandType: KClass<out TCommand>) {
//        if (!commandStore.isRegistered(commandType)) {
//            throw MissingHandlerException(commandType)
//        }
//    }

//    private fun <TCommand : Command> ensureNoOtherCommandHandlers(commandType: KClass<out TCommand>) {
//        if (commandStore.isRegistered(commandType)) {
//            throw TooManyHandlersException(commandType)
//        }
//    }

    private suspend fun <TMessage : Message> getBus(
        store: MessageStore<in TMessage>,
        handlers: List<MessageHandler<TMessage>>,
    ): MiddlewareHandler<TMessage> {
        val finalHandler: suspend (TMessage) -> Any? = { message: TMessage ->
            store.handle(message, handlers)
        }
        val bus = createMiddlewareChain(finalHandler, middlewares)

        return bus
    }
}
