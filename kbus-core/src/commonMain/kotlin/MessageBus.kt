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

// TODO remove default handlerLocator
open class MessageBus(
    protected val handlerLocator: MessageHandlerLocator = PersistingHandlerLocator(),
    transactionManager: TransactionManager? = null,
    val middlewares: List<Middleware> = emptyList(),
) {
    private val busAccess =
        object : BusAccess {
            override suspend fun <TEvent : Event> dispatch(event: TEvent) =
                this@MessageBus.dispatch(event)
        }
    private val eventDispatcher = EventDispatcher(handlerLocator::handlersFor, middlewares)
    protected val commandExecutor =
        CommandExecutor(
            transactionManager,
            middlewares,
            busAccess,
            DefaultCommandDependenciesFactory(null),
        )
    protected val queryFetcher = QueryFetcher(middlewares)

    suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
        command: TCommand
    ): TResult {
        val handlerCreator = { commandDependencies: CommandDependencies ->
            (handlerLocator.handlerFor(command, commandDependencies)
                ?: throw MissingHandlerException(command::class))
        }

        return commandExecutor.execute(command, handlerCreator)
    }

    suspend fun <TQuery : Query<TResult>, TResult : KBusResult> fetch(query: TQuery): TResult {
        val handler =
            (handlerLocator.handlerFor(query) ?: throw MissingHandlerException(query::class))

        return queryFetcher.fetch(query, handler)
    }

    private suspend fun <TEvent : Event> dispatch(event: TEvent) {
        val handlers = handlerLocator.handlersFor(event)

        eventDispatcher.dispatch(event, handlers)
    }
}
