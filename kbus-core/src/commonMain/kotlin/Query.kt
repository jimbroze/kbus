package com.jimbroze.kbus.core

abstract class Query : Message() {
    override val messageType: String = "query"
}

interface QueryHandler<TQuery : Query, TReturn : Any> : MessageHandler<TQuery> {
    override suspend fun handle(message: TQuery): TReturn
}
