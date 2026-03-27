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
    suspend fun <TEvent : Event> dispatchIntegrationEvent(
        event: TEvent,
        handlers: List<EventHandler<TEvent>> = emptyList(),
    ) {
        val finalHandler: suspend (TEvent) -> Unit = { message: TEvent ->
            handlers.forEach { handler -> dispatchAsync(message, handler) }
        }

        dispatchToHandlers(finalHandler, event)
    }

    override suspend fun <TEvent : DomainEvent> dispatchDomainEvent(
        event: TEvent,
        unitOfWork: UnitOfWork<*>,
    ) {
        val handlers = getHandlers(event)
        val eventErrorStrategy = errorStrategyFor(event)

        val finalHandler: suspend (TEvent) -> Unit = { message: TEvent ->
            val aggregatedExceptions = mutableListOf<Exception>()
            val lastIndex = handlers.lastIndex

            handlers.forEachIndexed { index, handler ->
                val isLastHandler = (index == lastIndex)
                val dispatch = dispatchSyncOrAsync(message, handler)

                val dispatchWithErrorHandling =
                    addErrorHandlingToDispatch(
                        eventErrorStrategy,
                        message,
                        handler,
                        dispatch,
                        aggregatedExceptions,
                        isLastHandler,
                    )

                dispatchAtCorrectTime(dispatchWithErrorHandling, unitOfWork, handler)
            }
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

    private suspend fun dispatchAtCorrectTime(
        dispatch: suspend () -> Unit,
        unitOfWork: UnitOfWork<*>,
        handler: EventHandler<DomainEvent>,
    ) {
        when (handler) {
            is DispatchImmediatelyInTransaction<*> -> dispatch()
            is DispatchAtEndOfTransaction<*> -> unitOfWork.addSecondaryWork { dispatch() }

            is DispatchAfterTransaction<*> -> unitOfWork.addPostCommitWork { dispatch() }
            // TODO outbox
            else -> unitOfWork.addPostCommitWork { dispatch() }
        }
    }

    private fun <TEvent : DomainEvent> addErrorHandlingToDispatch(
        eventErrorStrategy: ErrorStrategy,
        message: TEvent,
        handler: EventHandler<DomainEvent>,
        dispatch: suspend () -> Unit,
        aggregatedExceptions: MutableList<Exception>,
        isLastHandler: Boolean,
    ): suspend () -> Unit =
        when (eventErrorStrategy) {
            ErrorStrategy.FIRE_AND_FORGET -> {
                {
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        dispatch()
                    } catch (e: Exception) {
                        handleFailure(message, handler, e)
                    }
                }
            }
            ErrorStrategy.FAIL_FAST -> dispatch
            ErrorStrategy.CONTINUE_AND_AGGREGATE -> {
                {
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        dispatch()
                    } catch (e: Exception) {
                        aggregatedExceptions.add(e)
                    }
                    if (isLastHandler && aggregatedExceptions.isNotEmpty())
                        throw MultipleException(aggregatedExceptions)
                }
            }
        }

    private fun <TEvent : DomainEvent> dispatchSyncOrAsync(
        message: TEvent,
        handler: EventHandler<DomainEvent>,
    ): suspend () -> Unit = suspend {
        when (handler) {
            is DispatchSynchronously<*> -> {
                handler.handle(message)
            }
            is DispatchAsynchronously<*> -> dispatchAsync(message, handler)
            else -> dispatchAsync(message, handler)
        }
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
