typealias MiddlewareHandler<TMessage> = suspend (TMessage) -> Any?

interface Middleware {
    suspend fun <TMessage : Message> handle(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage>,
    ): Any?
}

suspend fun <TMessage : Message> createMiddlewareChain(
    finalHandler: MiddlewareHandler<TMessage>,
    middlewares: List<Middleware>,
): MiddlewareHandler<TMessage> {
    var lastHandler = finalHandler

    middlewares.reversed().forEach {
        val currentHandler = lastHandler
        lastHandler = { message: TMessage -> it.handle(message, currentHandler) }
    }

    return lastHandler
}
