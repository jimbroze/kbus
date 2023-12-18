interface Middleware {
    fun <TMessage : Message> handle(message: TMessage, nextMiddleware: (TMessage) -> Any?): Any?
    fun messageApplicable(message: Message): Boolean
}

fun <TMessage : Message> createMiddlewareChain(
    finalHandler: (TMessage) -> Any?,
    middlewares: List<Middleware>
): (TMessage) -> Any? {
    var lastHandler = finalHandler
    middlewares.reversed().forEach() {
        val currentHandler = lastHandler
        lastHandler = { message -> it.handle(message, currentHandler) }
    }

    return lastHandler
}
