package com.jimbroze.kbus.core

abstract class CanDispatchIntegrationEvent {
    private lateinit var bus: BusAccess

    fun setBus(bus: BusAccess) {
        this.bus = bus
    }

    suspend fun <TEvent : IntegrationEvent> dispatch(event: TEvent) {
        bus.dispatch(event)
    }
}

interface BusAccess {
    suspend fun <TEvent : Event> dispatch(event: TEvent)
}

interface IMessageBus {
    suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
        command: TCommand
    ): TResult

    suspend fun <TQuery : Query<TResult>, TResult : KBusResult> fetch(query: TQuery): TResult
}

abstract class BaseMessageBus(
    protected val handlerLocator: MessageHandlerLocator,
    transactionManager: TransactionManager?,
    protected val middlewares: List<Middleware>,
) : IMessageBus {
    private val busAccess =
        object : BusAccess {
            override suspend fun <TEvent : Event> dispatch(event: TEvent) =
                this@BaseMessageBus.dispatch(event)
        }
    protected val eventDispatcher = EventDispatcher(handlerLocator::handlersFor, middlewares)
    protected val commandExecutor =
        CommandExecutor(
            transactionManager,
            middlewares,
            busAccess,
            DefaultCommandDependenciesFactory(null),
        )
    protected val queryFetcher = QueryFetcher(middlewares)

    override suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
        command: TCommand
    ): TResult {
        val handlerCreator = { commandDependencies: CommandDependencies ->
            (handlerLocator.handlerFor(command, commandDependencies)
                ?: throw MissingHandlerException(command::class))
        }

        return commandExecutor.execute(command, handlerCreator)
    }

    override suspend fun <TQuery : Query<TResult>, TResult : KBusResult> fetch(
        query: TQuery
    ): TResult {
        val handlerCreator = {
            (handlerLocator.handlerFor(query) ?: throw MissingHandlerException(query::class))
        }

        return queryFetcher.fetch(query, handlerCreator)
    }

    private suspend fun <TEvent : Event> dispatch(event: TEvent) {
        val handlers = handlerLocator.handlersFor(event)

        eventDispatcher.dispatch(event, handlers)
    }
}

// TODO remove default handlerLocator
class MessageBus(
    handlerLocator: MessageHandlerLocator = PersistingHandlerLocator(),
    transactionManager: TransactionManager? = null,
    middlewares: List<Middleware> = emptyList(),
) : BaseMessageBus(handlerLocator, transactionManager, middlewares)
