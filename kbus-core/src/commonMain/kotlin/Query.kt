package com.jimbroze.kbus.core

abstract class Query<TReturn : Any?, TFailure : MessageFailure> :
    ResultReturningMessage<TReturn, TFailure> {
    override val messageType: String = "query"

    final override fun toString(): String = this::class.simpleName ?: "Query"
}

interface QueryHandler<
    TQuery : Query<TReturn, TFailure>,
    TReturn : Any?,
    TFailure : MessageFailure,
> : ResultReturningMessageHandler<TQuery, TReturn, TFailure> {
    override suspend fun handle(message: TQuery): BusResult<TReturn, TFailure>
}
