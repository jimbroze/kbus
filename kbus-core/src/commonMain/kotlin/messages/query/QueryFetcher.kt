package com.jimbroze.kbus.core.messages.query

import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.middleware.infrastructure.Middleware
import com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareInvocationContextFactory
import com.jimbroze.kbus.core.middleware.infrastructure.createMiddlewareChain

class QueryFetcher(
    private val middlewares: List<Middleware>,
    private val contextFactory: MiddlewareInvocationContextFactory,
) {
    suspend fun <TResult : KBusResult, TQuery : Query<TResult>> fetch(
        query: TQuery,
        createHandler: () -> QueryHandler<TQuery, TResult>,
    ): TResult {
        val handler = createHandler()

        val finalHandler: suspend (TQuery) -> KBusResult = { message: TQuery ->
            handler.handle(message)
        }

        val execute =
            createMiddlewareChain(finalHandler, middlewares, contextFactory.contextFor(null))

        @Suppress("UNCHECKED_CAST")
        return execute(query) as TResult
    }
}
