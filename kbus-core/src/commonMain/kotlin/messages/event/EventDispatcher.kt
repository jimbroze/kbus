package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.createMiddlewareChain
import com.jimbroze.kbus.core.uow.UnitOfWork
import com.jimbroze.kbus.domain.DomainEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private enum class ErrorStrategy {
    FIRE_AND_FORGET,
    FAIL_FAST,
    CONTINUE_AND_AGGREGATE,
}

private fun errorStrategyFor(event: DomainEvent): ErrorStrategy =
    when (event) {
        is FailFastDomainEvent -> ErrorStrategy.FAIL_FAST
        is ContinueAndAggregateDomainEvent -> ErrorStrategy.CONTINUE_AND_AGGREGATE
        is FireAndForgetDomainEvent -> ErrorStrategy.FIRE_AND_FORGET
        else -> ErrorStrategy.FIRE_AND_FORGET
    }

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
        val errorStrategy = errorStrategyFor(event)

        val finalHandler: suspend (TEvent) -> Unit = { message: TEvent ->
            val aggregatedExceptions = mutableListOf<Exception>()

            for (handler in handlers) {
                val dispatch = suspend {
                    when (handler) {
                        is DispatchSynchronously<*> -> dispatchSync(message, handler)
                        is DispatchAsynchronously<*> -> dispatchAsync(message, handler)
                        else -> dispatchAsync(message, handler)
                    }
                }

                val dispatchWithErrorHandling: suspend () -> Unit =
                    when (errorStrategy) {
                        ErrorStrategy.FIRE_AND_FORGET -> {
                            { fireAndForget(message, handler) { dispatch() } }
                        }
                        ErrorStrategy.FAIL_FAST -> dispatch
                        ErrorStrategy.CONTINUE_AND_AGGREGATE -> {
                            { aggregateExceptions(aggregatedExceptions) { dispatch() } }
                        }
                    }

                when (handler) {
                    is DispatchImmediatelyInTransaction<*> -> dispatchWithErrorHandling()
                    is DispatchAtEndOfTransaction<*> ->
                        unitOfWork.addSecondaryWork { dispatchWithErrorHandling() }
                    is DispatchAfterTransaction<*> ->
                        unitOfWork.addPostCommitWork { dispatchWithErrorHandling() }
                    // TODO outbox
                    else -> unitOfWork.addPostCommitWork { dispatchWithErrorHandling() }
                }
            }

            if (aggregatedExceptions.isNotEmpty()) {
                throw MultipleException(aggregatedExceptions)
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
                handleFailure(message, handler, e)
            }
        }
    }

    private suspend fun <TEvent : Event> fireAndForget(
        message: TEvent,
        handler: EventHandler<TEvent>,
        dispatch: suspend () -> Unit,
    ) {
        @Suppress("TooGenericExceptionCaught")
        try {
            dispatch()
        } catch (e: Exception) {
            handleFailure(message, handler, e)
        }
    }

    private suspend fun aggregateExceptions(
        exceptions: MutableList<Exception>,
        dispatch: suspend () -> Unit,
    ) {
        @Suppress("TooGenericExceptionCaught")
        try {
            dispatch()
        } catch (e: Exception) {
            exceptions.add(e)
        }
    }

    private fun <TEvent : Event> handleFailure(
        message: TEvent,
        handler: EventHandler<TEvent>,
        e: Exception,
    ) {
        // TODO Log the error, send to a Dead Letter Queue (DLQ)
        println(
            "Handler ${handler::class.simpleName} failed for event $message. Error: ${e.message}"
        )
    }
}
