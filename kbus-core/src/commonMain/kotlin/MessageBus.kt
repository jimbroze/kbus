package com.jimbroze.kbus.core

import kotlin.js.JsName
import kotlin.reflect.KClass

abstract class CanAccessBus {
    private lateinit var bus: BusAccess

    fun setBus(bus: BusAccess) {
        this.bus = bus
    }

    suspend fun dispatch(event: Event) {
        bus.dispatch(event)
    }
}

interface BusAccess {
    suspend fun <TEvent : Event> dispatch(event: TEvent)
}

@Suppress("TooManyFunctions")
open class MessageBus(
    protected val handlerLocator: HandlerLocator = PersistingHandlerLocator(),
    val middlewares: List<Middleware> = emptyList(),
) : BusAccess {
    private val eventDispatcher = EventDispatcher(middlewares)
    private val commandExecutor = CommandExecutor(middlewares, this as BusAccess)
    private val queryFetcher = QueryFetcher(middlewares)

    suspend fun <TCommand : Command> execute(command: TCommand): BusResult<*, *> {
        val handler =
            handlerLocator.messageMapper.handlerFor(command)
                ?: throw MissingHandlerException(command::class)

        return commandExecutor.execute(command, handler)
    }

    @JsName("executeCommand")
    suspend fun <TCommand : Command, TReturn : Any?, TFailure : FailureReason> execute(
        command: TCommand,
        handler: CommandHandler<TCommand, TReturn, TFailure>,
    ): BusResult<TReturn, TFailure> {
        return commandExecutor.execute(command, handler)
    }

    suspend fun <TCommand : Command, TReturn : Any?, TFailure : FailureReason> execute(
        command: TCommand,
        handlerType: KClass<out CommandHandler<TCommand, TReturn, TFailure>>,
    ): BusResult<TReturn, TFailure> {
        val handler = handlerLocator.factory.create(handlerType)

        return commandExecutor.execute(command, handler)
    }

    suspend fun <TQuery : Query> fetch(query: TQuery): BusResult<*, *> {
        val handler =
            handlerLocator.messageMapper.handlerFor(query)
                ?: throw MissingHandlerException(query::class)

        return queryFetcher.fetch(query, handler)
    }

    suspend fun <TQuery : Query, TReturn : Any?, TFailure : FailureReason> fetch(
        query: TQuery,
        handler: QueryHandler<TQuery, TReturn, TFailure>,
    ): BusResult<TReturn, TFailure> {
        return queryFetcher.fetch(query, handler)
    }

    suspend fun <TQuery : Query, TReturn : Any?, TFailure : FailureReason> fetch(
        query: TQuery,
        handlerType: KClass<out QueryHandler<TQuery, TReturn, TFailure>>,
    ): BusResult<TReturn, TFailure> {
        val handler = handlerLocator.factory.create(handlerType)

        return queryFetcher.fetch(query, handler)
    }

    override suspend fun <TEvent : Event> dispatch(event: TEvent) {
        val handlers = handlerLocator.messageMapper.handlersFor(event)

        eventDispatcher.dispatch(event, handlers)
    }

    suspend fun <TEvent : Event> dispatch(
        event: TEvent,
        handlers: List<EventHandler<TEvent>> = emptyList(),
    ) {
        eventDispatcher.dispatch(event, handlers)
    }
}
