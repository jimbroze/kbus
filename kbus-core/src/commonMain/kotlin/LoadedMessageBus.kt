package com.jimbroze.kbus.core

import kotlin.reflect.KClass

interface LoadedCommand<
    TCommand : Command,
    THandler : CommandHandler<TCommand, TReturn, TFailure>,
    TReturn : Any?,
    TFailure : FailureReason,
> {
    val command: TCommand
    val handler: KClass<THandler>

    suspend fun handle(handler: THandler): BusResult<TReturn, TFailure> = handler.handle(command)
}

interface LoadedQuery<
    TQuery : Query,
    THandler : QueryHandler<TQuery, TReturn, TFailure>,
    TReturn : Any?,
    TFailure : FailureReason,
> {
    val query: TQuery
    val handler: KClass<THandler>

    suspend fun handle(handler: THandler): BusResult<TReturn, TFailure> = handler.handle(query)
}

open class LoadedMessageBus(
    handlerLocator: HandlerLocator,
    transactionManager: TransactionManager,
    middleware: List<Middleware>,
) : MessageBus(handlerLocator, transactionManager, middleware) {
    suspend fun <TCommand : Command, TReturn : Any?, TFailure : FailureReason> execute(
        loadedCommand:
            LoadedCommand<TCommand, CommandHandler<TCommand, TReturn, TFailure>, TReturn, TFailure>
    ): BusResult<TReturn, TFailure> = this.execute(loadedCommand.command, loadedCommand.handler)

    suspend fun <TQuery : Query, TReturn : Any?, TFailure : FailureReason> fetch(
        loadedQuery: LoadedQuery<TQuery, QueryHandler<TQuery, TReturn, TFailure>, TReturn, TFailure>
    ): BusResult<TReturn, TFailure> = this.fetch(loadedQuery.query, loadedQuery.handler)
}
