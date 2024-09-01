package com.jimbroze.kbus.core

abstract class Query : Message() {
    override val messageType: String = "query"
}

interface QueryHandler<
    TQuery : Query,
    TReturn : Any?,
    TFailure : ResultFailure,
> : MessageHandler<TQuery>, ResultReturningHandler<TQuery, TReturn, TFailure> {
    override suspend fun handle(message: TQuery): BusResult<TReturn, TFailure>
}
