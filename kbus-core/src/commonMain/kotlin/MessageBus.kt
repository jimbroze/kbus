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
    private val commandExecutor =
        CommandExecutor(
            transactionManager,
            middlewares,
            busAccess,
            DefaultCommandDependenciesFactory(null),
        )
    private val queryFetcher = QueryFetcher(middlewares)

    suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
        command: TCommand
    ): TResult {
        val handlerCreator = { commandDependencies: CommandDependencies ->
            (handlerLocator.handlerFor(command, commandDependencies)
                ?: throw MissingHandlerException(command::class))
        }

        return commandExecutor.execute(command, handlerCreator)
    }

    //    @JvmName("executeCommand")
    //    @JsName("executeCommand")
    //    suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
    //        command: TCommand,
    //        handler: CommandHandler<TCommand, TResult>,
    //    ): TResult {
    //        return commandExecutor.execute(command) { handler }
    //    }

    //    suspend fun <
    //        TCommand : Command<TResult>,
    //        THandler : CommandHandler<TCommand, TResult>,
    //        TResult : KBusResult,
    //    > execute(command: TCommand, handlerType: KClass<THandler>): TResult {
    //        val createHandler = { commandDependencies: CommandDependencies ->
    //            handlerLocator.factory.create(handlerType, commandDependencies)
    //        }
    //
    //        return commandExecutor.execute(command, createHandler)
    //    }

    suspend fun <TQuery : Query<TResult>, TResult : KBusResult> fetch(query: TQuery): TResult {
        val handler =
            (handlerLocator.handlerFor(query) ?: throw MissingHandlerException(query::class))

        return queryFetcher.fetch(query, handler)
    }

    //    suspend fun <
    //        TQuery : Query<TResult>,
    //        THandler : QueryHandler<TQuery, TResult>,
    //        TResult : KBusResult,
    //    > fetch(query: TQuery, handler: THandler): TResult {
    //        return queryFetcher.fetch(query, handler)
    //    }
    //
    //    suspend fun <
    //        TQuery : Query<TResult>,
    //        THandler : QueryHandler<TQuery, TResult>,
    //        TResult : KBusResult,
    //    > fetch(query: TQuery, handlerType: KClass<THandler>): TResult {
    //        val handler = handlerLocator.factory.create(handlerType)
    //
    //        return queryFetcher.fetch(query, handler)
    //    }

    private suspend fun <TEvent : Event> dispatch(event: TEvent) {
        val handlers = handlerLocator.handlersFor(event)

        eventDispatcher.dispatch(event, handlers)
    }
}
