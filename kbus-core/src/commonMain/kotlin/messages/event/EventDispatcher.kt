package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.createMiddlewareChain
import com.jimbroze.kbus.core.uow.UnitOfWork
import com.jimbroze.kbus.domain.DomainEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private enum class ErrorStrategy {
    FIRE_AND_FORGET,
    FAIL_FAST,
    CONTINUE_AND_AGGREGATE,
}

enum class DispatchPhase {
    IMMEDIATE,
    SECONDARY,
    POST_COMMIT,
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
            val handlersByPhase = handlers.groupBy { dispatchPhase(it, eventErrorStrategy) }
            handlersByPhase.forEach { (phase, phaseHandlers) ->
                val aggregatedExceptions = mutableListOf<Exception>()

                val dispatchHandlersWithErrorHandling = phaseHandlers.mapIndexed { index, handler ->
                    addErrorHandlingToDispatch(
                        eventErrorStrategy,
                        message,
                        handler,
                        { -> handler.handle(message) },
                        aggregatedExceptions,
                        index == phaseHandlers.lastIndex,
                    )
                }

                val dispatchHandlersWithConcurrency: suspend () -> Unit = {
                    if (event is DispatchSequentially) {
                        dispatchHandlersWithErrorHandling.forEach { it() }
                    } else {
                        coroutineScope {
                            dispatchHandlersWithErrorHandling
                                .map { dispatchHandler -> async { dispatchHandler() } }
                                .awaitAll()
                        }
                    }
                }

                dispatchHandlersInPhase(dispatchHandlersWithConcurrency, unitOfWork, message, phase)
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

    private fun dispatchPhase(
        handler: EventHandler<*>,
        errorStrategy: ErrorStrategy,
    ): DispatchPhase {
        val phase =
            when (handler) {
                is DispatchImmediatelyInTransaction<*> -> DispatchPhase.IMMEDIATE
                is DispatchAtEndOfTransaction<*> -> DispatchPhase.SECONDARY
                is DispatchAfterTransaction<*> -> DispatchPhase.POST_COMMIT
                else -> DispatchPhase.POST_COMMIT
            }

        validateDispatchPhase(phase, errorStrategy)

        return phase
    }

    private fun validateDispatchPhase(phase: DispatchPhase, errorStrategy: ErrorStrategy) {
        if (
            phase == DispatchPhase.POST_COMMIT &&
                errorStrategy in
                    listOf(ErrorStrategy.FAIL_FAST, ErrorStrategy.CONTINUE_AND_AGGREGATE)
        )
            error(
                "events with fail-fast or aggregate error strategies cannot be " +
                    "dispatched outside the unit of work"
            )
    }

    private suspend fun <TEvent : DomainEvent> dispatchHandlersInPhase(
        dispatch: suspend () -> Unit,
        unitOfWork: UnitOfWork<*>,
        message: TEvent,
        phase: DispatchPhase,
    ) {
        when (phase) {
            DispatchPhase.IMMEDIATE -> dispatch()
            DispatchPhase.SECONDARY -> unitOfWork.addSecondaryWork { dispatch() }
            DispatchPhase.POST_COMMIT ->
                unitOfWork.addPostCommitWork {
                    runCatching { dispatch() }.onFailure { handleFailure(message, it) }
                }
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
                        handleFailure(message, e, handler)
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

    private fun <TEvent : Event> dispatchAsync(message: TEvent, handler: EventHandler<TEvent>) {
        dispatcherScope.launch {
            @Suppress("TooGenericExceptionCaught")
            try {
                handler.handle(message)
            } catch (e: Exception) {
                handleFailure(message, e, handler)
            }
        }
    }

    private fun <TEvent : Event> handleFailure(
        message: TEvent,
        e: Throwable,
        handler: EventHandler<TEvent>? = null,
    ) {
        // TODO Log the error, send to a Dead Letter Queue (DLQ)
        val handlerName = if (handler == null) "unknown" else handler::class.simpleName
        println("Handler $handlerName failed for event $message. Error: ${e.message}")
    }
}
