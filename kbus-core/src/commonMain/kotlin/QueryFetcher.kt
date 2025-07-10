package com.jimbroze.kbus.core

class QueryFetcher(private val middlewares: List<Middleware>) {
    suspend fun <TResult : KBusResult, TQuery : Query<TResult>> fetch(
        query: TQuery,
        handler: QueryHandler<TQuery, TResult>,
    ): TResult {
        val finalHandler: suspend (TQuery) -> KBusResult = { message: TQuery ->
            handler.handle(message)
        }

        val execute = createMiddlewareChain(finalHandler, middlewares)

        @Suppress("UNCHECKED_CAST")
        return execute(query) as TResult
    }
}
