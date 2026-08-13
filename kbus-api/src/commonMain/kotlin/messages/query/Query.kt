package com.jimbroze.kbus.api.messages.query

import com.jimbroze.kbus.api.common.ResultReturningMessage
import com.jimbroze.kbus.api.result.KBusResult
import com.jimbroze.kbus.api.result.ResultReturningMessageHandler

abstract class Query<TResult : KBusResult> : ResultReturningMessage<TResult> {
    override val messageType: String = "query"

    final override fun toString(): String = this::class.simpleName ?: "Query"
}

abstract class QueryHandler<TQuery : Query<TResult>, TResult : KBusResult> :
    ResultReturningMessageHandler<TQuery, TResult> {
    abstract override suspend fun handle(message: TQuery): TResult
}
