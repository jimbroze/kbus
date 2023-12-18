interface Middleware {
    fun <TMessage : Message> handle(message: TMessage, nextMiddleware: (TMessage) -> Any?): Any?
    fun messageApplicable(message: Message): Boolean
}

fun <TMessage : Message> createMiddlewareChain(
    finalHandler: (TMessage) -> Any?,
    middlewares: List<Middleware>
): (TMessage) -> Any? {
//    fun handleMiddleware(
//        middleware: Middleware,
//        nextClosure: (TMessage) -> Any?,
//        message: TMessage
//    ): Any? = middleware.handle(message, nextClosure)

    var nextHandler = finalHandler
    middlewares.reversed().forEach { nextHandler = { message -> it.handle(message, nextHandler) } }
//        nextHandler = { message -> handleMiddleware(thisMiddleware, nextHandler, message) }

    return nextHandler
}
