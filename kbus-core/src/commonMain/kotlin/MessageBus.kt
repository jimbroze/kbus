package com.jimbroze.kbus.core

import kotlin.jvm.JvmName
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

// TODO remove default handlerLocator
open class MessageBus(
    protected val handlerLocator: HandlerLocator = PersistingHandlerLocator(),
    transactionManager: TransactionManager? = null,
    val middlewares: List<Middleware> = emptyList(),
) : BusAccess {
    private val eventDispatcher =
        EventDispatcher(handlerLocator.messageMapper::handlersFor, middlewares)
    private val commandExecutor =
        CommandExecutor(
            transactionManager,
            middlewares,
            this as BusAccess,
            DefaultCommandDependenciesFactory(null),
        )
    private val queryFetcher = QueryFetcher(middlewares)

    suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
        command: TCommand
    ): TResult {
        val handlerCreator = { commandDependencies: CommandDependencies ->
            (handlerLocator.messageMapper.handlerFor(command, commandDependencies)
                ?: throw MissingHandlerException(command::class))
        }

        return commandExecutor.execute(command, handlerCreator)
    }

    @JvmName("executeCommand")
    suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
        command: TCommand,
        handler: CommandHandler<TCommand, TResult>,
    ): TResult {
        return commandExecutor.execute(command) { handler }
    }

    suspend fun <
        TCommand : Command<TResult>,
        THandler : CommandHandler<TCommand, TResult>,
        TResult : KBusResult,
    > execute(command: TCommand, handlerType: KClass<THandler>): TResult {
        val createHandler = { commandDependencies: CommandDependencies ->
            handlerLocator.factory.create(handlerType, commandDependencies)
        }

        return commandExecutor.execute(command, createHandler)
    }

    suspend fun <TQuery : Query<TResult>, TResult : KBusResult> fetch(query: TQuery): TResult {
        val handler =
            (handlerLocator.messageMapper.handlerFor(query)
                ?: throw MissingHandlerException(query::class))

        return queryFetcher.fetch(query, handler)
    }

    suspend fun <
        TQuery : Query<TResult>,
        THandler : QueryHandler<TQuery, TResult>,
        TResult : KBusResult,
    > fetch(query: TQuery, handler: THandler): TResult {
        return queryFetcher.fetch(query, handler)
    }

    suspend fun <
        TQuery : Query<TResult>,
        THandler : QueryHandler<TQuery, TResult>,
        TResult : KBusResult,
    > fetch(query: TQuery, handlerType: KClass<THandler>): TResult {
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
