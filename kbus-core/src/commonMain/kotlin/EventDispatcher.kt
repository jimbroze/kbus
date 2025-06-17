package com.jimbroze.kbus.core

class EventDispatcher(private val middlewares: List<Middleware>) {
    suspend fun <TEvent : Event> dispatch(
        event: TEvent,
        handlers: List<EventHandler<TEvent>> = emptyList(),
    ) {
        val finalHandler: suspend (TEvent) -> Any? = { message: TEvent ->
            handlers.forEach { handler -> handler.handle(message) }
        }

        val execute = createMiddlewareChain(finalHandler, middlewares)

        execute(event)
    }
}
