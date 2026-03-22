package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.createMiddlewareChain
import com.jimbroze.kbus.core.uow.UnitOfWork
import com.jimbroze.kbus.domain.DomainEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface DomainEventDispatcher {
    suspend fun <TEvent : DomainEvent> dispatchDomainEvent(event: TEvent, unitOfWork: UnitOfWork<*>)
}

typealias GetHandlers<TEvent> = (event: TEvent) -> List<EventHandler<TEvent>>

class EventDispatcher(
    val getHandlers: GetHandlers<DomainEvent>,
    val middlewares: List<Middleware>,
    private val dispatcherScope: CoroutineScope,
) : DomainEventDispatcher {
    override suspend fun <TEvent : DomainEvent> dispatchDomainEvent(
        event: TEvent,
        unitOfWork: UnitOfWork<*>,
    ) {
        val handlers = getHandlers(event)

        val finalHandler: suspend (TEvent) -> Unit = { message: TEvent ->
            handlers.forEach { handler ->
                val dispatch = suspend {
                    when (handler) {
                        is DispatchSynchronously<*> -> dispatchSync(message, handler)
                        is DispatchAsynchronously<*> -> dispatchAsync(message, handler)
                        else -> dispatchAsync(message, handler)
                    }
                }
                when (handler) {
                    is DispatchImmediatelyInTransaction<*> -> dispatch()
                    is DispatchAtEndOfTransaction<*> -> unitOfWork.addSecondaryWork { dispatch() }
                    is DispatchAfterTransaction<*> -> unitOfWork.addPostCommitWork { dispatch() }
                    // TODO outbox
                    else -> unitOfWork.addPostCommitWork { dispatch() }
                }
            }
        }

        dispatchToHandlers(finalHandler, event)
    }

    suspend fun <TEvent : Event> dispatchIntegrationEvent(
        event: TEvent,
        handlers: List<EventHandler<TEvent>> = emptyList(),
    ) {
        val finalHandler: suspend (TEvent) -> Unit = { message: TEvent ->
            handlers.forEach { handler -> dispatchAsync(message, handler) }
        }

        dispatchToHandlers(finalHandler, event)
    }

    private suspend fun <TEvent : Event> dispatchToHandlers(
        finalHandler: suspend (TEvent) -> Unit,
        event: TEvent,
    ) {
        val execute = createMiddlewareChain(finalHandler, middlewares)
        execute(event)
    }

    private suspend fun <TEvent : Event> dispatchSync(
        message: TEvent,
        handler: EventHandler<TEvent>,
    ) {
        handler.handle(message)
    }

    private fun <TEvent : Event> dispatchAsync(message: TEvent, handler: EventHandler<TEvent>) {
        dispatcherScope.launch {
            @Suppress("TooGenericExceptionCaught")
            try {
                handler.handle(message)
            } catch (e: Exception) {
                handleAsyncFailure(message, handler, e)
            }
        }
    }

    private fun <TEvent : Event> handleAsyncFailure(
        message: TEvent,
        handler: EventHandler<TEvent>,
        e: Exception,
    ) {
        // TODO Log the error, send to a Dead Letter Queue (DLQ)
        println(
            "Async handler ${handler::class.simpleName} failed for event $message. Error: ${e.message}"
        )
    }
}
