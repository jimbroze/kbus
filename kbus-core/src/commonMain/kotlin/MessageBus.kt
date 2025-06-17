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
    suspend fun <TCommand : Command> execute(command: TCommand): BusResult<*, *> {
        val handler =
            handlerLocator.messageMapper.handlerFor(command)
                ?: throw MissingHandlerException(command::class)

        handler.setBus(this)

        val finalHandler: suspend (TCommand) -> Any? = { message: TCommand ->
            handler.handle(message)
        }

        val execute = createMiddlewareChain(finalHandler, middlewares)

        return execute(command) as BusResult<*, *>
    }

    @JsName("executeCommand")
    suspend fun <TCommand : Command, TReturn : Any?, TFailure : FailureReason> execute(
        command: TCommand,
        handler: CommandHandler<TCommand, TReturn, TFailure>,
    ): BusResult<TReturn, TFailure> {
        handler.setBus(this)
        val finalHandler: suspend (TCommand) -> Any? = { message: TCommand ->
            handler.handle(message)
        }

        val execute = createMiddlewareChain(finalHandler, middlewares)

        return execute(command) as BusResult<TReturn, TFailure>
    }

    suspend fun <TCommand : Command, TReturn : Any?, TFailure : FailureReason> execute(
        command: TCommand,
        handlerType: KClass<CommandHandler<TCommand, TReturn, TFailure>>,
    ): BusResult<TReturn, TFailure> {
        val handler = handlerLocator.factory.create(handlerType)
        return execute(command, handler)
    }

    suspend fun <TCommand : Command, TReturn : Any?, TFailure : FailureReason> fetch(
        command: TCommand,
        handler: CommandHandler<TCommand, TReturn, TFailure>,
    ): BusResult<TReturn, TFailure> {
        return execute(command, handler)
    }

    suspend fun <TCommand : Command, TReturn : Any?, TFailure : FailureReason> fetch(
        command: TCommand,
        handlerType: KClass<CommandHandler<TCommand, TReturn, TFailure>>,
    ): BusResult<TReturn, TFailure> {
        return execute(command, handlerType)
    }

    @JsName("executeQuery")
    suspend fun <TQuery : Query, TReturn : Any, TFailure : FailureReason> execute(
        query: TQuery,
        handler: QueryHandler<TQuery, TReturn, TFailure>,
    ): BusResult<TReturn, TFailure> {
        val finalHandler: suspend (TQuery) -> Any? = { message: TQuery -> handler.handle(message) }

        val execute = createMiddlewareChain(finalHandler, middlewares)

        return execute(query) as BusResult<TReturn, TFailure>
    }

    suspend fun <TQuery : Query> fetch(query: TQuery): BusResult<*, *> {
        val handler =
            handlerLocator.messageMapper.handlerFor(query)
                ?: throw MissingHandlerException(query::class)

        val finalHandler: suspend (TQuery) -> Any? = { message: TQuery -> handler.handle(message) }

        val execute = createMiddlewareChain(finalHandler, middlewares)

        return execute(query) as BusResult<*, *>
    }

    suspend fun <TQuery : Query, TReturn : Any?, TFailure : FailureReason> fetch(
        query: TQuery,
        handler: QueryHandler<TQuery, TReturn, TFailure>,
    ): BusResult<TReturn, TFailure> {
        val finalHandler: suspend (TQuery) -> Any? = { message: TQuery -> handler.handle(message) }

        val execute = createMiddlewareChain(finalHandler, middlewares)

        return execute(query) as BusResult<TReturn, TFailure>
    }

    suspend fun <TQuery : Query, TReturn : Any?, TFailure : FailureReason> fetch(
        query: TQuery,
        handlerType: KClass<QueryHandler<TQuery, TReturn, TFailure>>,
    ): BusResult<TReturn, TFailure> {
        val handler = handlerLocator.factory.create(handlerType)
        return fetch(query, handler)
    }

    override suspend fun <TEvent : Event> dispatch(event: TEvent) {
        val handlers = handlerLocator.messageMapper.handlersFor(event)

        val finalHandler: suspend (TEvent) -> Any? = { message: TEvent ->
            handlers.forEach { handler -> handler.handle(message) }
        }

        val execute = createMiddlewareChain(finalHandler, middlewares)

        execute(event)
    }

    suspend fun <TEvent : Event> dispatch(
        event: TEvent,
        handlers: List<EventHandler<TEvent>> = emptyList(),
    ) {
        val allHandlers = handlerLocator.messageMapper.handlersFor(event) + handlers

        val finalHandler: suspend (TEvent) -> Any? = { message: TEvent ->
            allHandlers.forEach { handler -> handler.handle(message) }
        }

        val execute = createMiddlewareChain(finalHandler, middlewares)

        execute(event)
    }
}
