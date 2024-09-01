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

//    suspend fun <TCommand : Command, TReturn : Any?> execute(
//        command: TCommand,
//        handler: GenericCommandHandler<TCommand, TReturn>,
//    ): CustomResult<TReturn, ResultExceptionOptions> {
////        ensureNoOtherCommandHandlers(command::class)
//
//        val commandBus = getBus(commandStore, listOfNotNull(handler))
//        return result(commandBus, command)
//    }

    suspend fun <TCommand : Command, TReturn : Any?, TFailure : FailureReason> execute(
        command: TCommand,
        handler: CommandHandler<TCommand, TReturn, TFailure>,
    ): BusResult<TReturn, TFailure> {
//        ensureNoOtherCommandHandlers(command::class)

        val commandBus = getBus(commandStore, listOfNotNull(handler))
        return result(commandBus, command)
    }

    suspend fun <TQuery : Query, TReturn : Any, TFailure : FailureReason> execute(
        query: TQuery,
        handler: QueryHandler<TQuery, TReturn, TFailure>,
    ): BusResult<TReturn, TFailure> {
        val queryBus = getBus(queryStore, listOfNotNull(handler))
        return result(queryBus, query)
    }

    suspend fun <TEvent : Event> dispatch(
        event: TEvent,
        handlers: List<EventHandler<TEvent>> = emptyList(),
    ) {
        val eventBus = getBus(eventStore, handlers)
        eventBus(event)
    }

//    fun <TCommand : Command, TReturn : Any?> register(

    fun <TEvent : Event> register(
        eventType: KClass<TEvent>,
        handlers: List<EventHandler<TEvent>>,
    ) {
        this.eventStore.registerHandlers(eventType, handlers)
    }

//    fun <TCommand : Command> deregister(commandType: KClass<TCommand>) {

    fun <TEvent : Event> deregister(
        messageType: KClass<TEvent>,
        handlers: List<EventHandler<TEvent>> = emptyList(),
    ) {
        this.eventStore.removeHandlers(messageType, handlers)
    }

//    fun <TCommand : Command> isRegistered(commandType: KClass<TCommand>): Boolean {

    fun <TEvent : Event> hasHandlers(eventType: KClass<TEvent>): Int {
        return this.eventStore.getHandlers(eventType).size
    }

//    private fun <TCommand : Command> ensureCommandHandlerExists(commandType: KClass<out TCommand>) {

//    private fun <TCommand : Command> ensureNoOtherCommandHandlers(commandType: KClass<out TCommand>) {

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

    //    }
//        }
//            throw TooManyHandlersException(commandType)
//        if (commandStore.isRegistered(commandType)) {
//    }
//        }
//            throw MissingHandlerException(commandType)
//        if (!commandStore.isRegistered(commandType)) {
//    }
//        return this.commandStore.isRegistered(commandType)
//    }
//        this.commandStore.removeHandlers(commandType)
//    }
//        this.commandStore.registerHandlers(messageType, listOfNotNull(handler))
//
//        ensureNoOtherCommandHandlers(messageType)
//    ) {
//        handler: CommandHandler<TCommand, TReturn>,
//        messageType: KClass<TCommand>,

    private suspend fun <TMessage : Message, TReturn : Any?, TFailure : FailureReason> result(
        messageBus: MiddlewareHandler<TMessage>,
        message: TMessage
    ): BusResult<TReturn, TFailure> {
        @Suppress("UNCHECKED_CAST")
        return messageBus(message) as BusResult<TReturn, TFailure>
    }
}
