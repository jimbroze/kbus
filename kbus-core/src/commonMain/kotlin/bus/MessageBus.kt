package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.contracts.bus.BusAccess
import com.jimbroze.kbus.contracts.common.MissingHandlerException
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.messages.command.CommandExecutor
import com.jimbroze.kbus.core.messages.command.DefaultCommandDependenciesFactory
import com.jimbroze.kbus.core.messages.event.EventDispatcher
import com.jimbroze.kbus.core.messages.query.QueryFetcher
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.registry.HandlerLocator
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus

interface IMessageBus {
    suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
        command: TCommand
    ): TResult

    suspend fun <TQuery : Query<TResult>, TResult : KBusResult> fetch(query: TQuery): TResult
}

// TODO allow middleware to get scope from bus?
abstract class BaseMessageBus(
    protected val handlerLocator: HandlerLocator,
    transactionManager: TransactionManager?,
    protected val middlewares: List<Middleware>,
    public val rootScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : IMessageBus {
    private val busAccess =
        object : BusAccess {
            override suspend fun <TEvent : IntegrationEvent> dispatch(event: TEvent) =
                this@BaseMessageBus.dispatchIntegration(event)
        }
    private val eventDispatcherScope =
        rootScope + SupervisorJob() + Dispatchers.Default + CoroutineName("KBus-Event-Dispatcher")
    protected val eventDispatcher =
        EventDispatcher(handlerLocator::handlersFor, middlewares, eventDispatcherScope)
    protected val commandExecutor =
        CommandExecutor(
            transactionManager,
            middlewares,
            busAccess,
            DefaultCommandDependenciesFactory(eventDispatcher),
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

    private suspend fun <TEvent : IntegrationEvent> dispatchIntegration(event: TEvent) {
        val handlers = handlerLocator.handlersFor(event)

        eventDispatcher.dispatchIntegrationEvent(event, handlers)
    }
}

class MessageBus(
    handlerLocator: HandlerLocator = PersistingHandlerLocator(),
    transactionManager: TransactionManager? = null,
    middlewares: List<Middleware> = emptyList(),
    rootScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : BaseMessageBus(handlerLocator, transactionManager, middlewares, rootScope)
