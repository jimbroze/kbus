package com.jimbroze.kbus.core.messages.query

import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.createMiddlewareChain
import com.jimbroze.kbus.core.result.KBusResult

class QueryFetcher(private val middlewares: List<Middleware>) {
    suspend fun <TResult : KBusResult, TQuery : Query<TResult>> fetch(
        query: TQuery,
        createHandler: () -> QueryHandler<TQuery, TResult>,
    ): TResult {
        val handler = createHandler()

        val finalHandler: suspend (TQuery) -> KBusResult = { message: TQuery ->
            handler.handle(message)
        }

        val execute = createMiddlewareChain(finalHandler, middlewares)

        @Suppress("UNCHECKED_CAST")
        return execute(query) as TResult
    }
}
