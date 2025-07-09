package com.jimbroze.kbus.core

class QueryFetcher(private val middlewares: List<Middleware>) {
    suspend fun <
        TQuery : Query<TReturn, TFailure>,
        TReturn : Any?,
        TFailure : MessageFailure,
    > fetch(
        query: TQuery,
        handler: QueryHandler<TQuery, TReturn, TFailure>,
    ): BusResult<TReturn, TFailure> {
        val finalHandler: suspend (TQuery) -> BusResult<TReturn, TFailure> = { message: TQuery ->
            handler.handle(message)
        }

        val execute = createMiddlewareChain(finalHandler, middlewares)

        return execute(query)
    }
}
