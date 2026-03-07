package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.contracts.bus.BusAccess
import com.jimbroze.kbus.contracts.common.MissingHandlerException
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.event.Event
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
import com.jimbroze.kbus.core.registry.PersistingHandlerLocator

interface IMessageBus {
    suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
        command: TCommand
    ): TResult

    suspend fun <TQuery : Query<TResult>, TResult : KBusResult> fetch(query: TQuery): TResult
}

abstract class BaseMessageBus(
    protected val handlerLocator: HandlerLocator,
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

class MessageBus(
    handlerLocator: HandlerLocator = PersistingHandlerLocator(),
    transactionManager: TransactionManager? = null,
    middlewares: List<Middleware> = emptyList(),
) : BaseMessageBus(handlerLocator, transactionManager, middlewares)
