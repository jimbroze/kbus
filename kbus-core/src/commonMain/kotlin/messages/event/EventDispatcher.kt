package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.createMiddlewareChain
import com.jimbroze.kbus.core.uow.NonReturningUnitOfWork
import com.jimbroze.kbus.core.uow.UnitOfWork
import com.jimbroze.kbus.domain.DomainEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
        val unitOfWork = NonReturningUnitOfWork()

        dispatchEvent(
            event,
            handlers.groupBy { DispatchPhase.POST_COMMIT },
            ErrorStrategy.FIRE_AND_FORGET,
            unitOfWork,
        )

        unitOfWork.execute()
    }

    override suspend fun <TEvent : DomainEvent> dispatchDomainEvent(
        event: TEvent,
        unitOfWork: UnitOfWork<*>,
    ) {
        val handlers = getHandlers(event)

        val eventErrorStrategy = errorStrategyFor(event)
        val handlersByPhase = handlers.groupBy { dispatchPhaseFor(it, eventErrorStrategy) }

        dispatchEvent(event, handlersByPhase, eventErrorStrategy, unitOfWork)
    }

    private suspend fun <TEvent : Event> dispatchEvent(
        event: TEvent,
        handlersByPhase: Map<DispatchPhase, List<EventHandler<TEvent>>>,
        eventErrorStrategy: ErrorStrategy,
        unitOfWork: UnitOfWork<*>,
    ) {
        val finalHandler: suspend (TEvent) -> Unit = { message: TEvent ->
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

                val dispatchHandlersWithConcurrency =
                    dispatchHandlersWithConcurrency(
                        event,
                        dispatchHandlersWithErrorHandling,
                        phase,
                        eventErrorStrategy,
                    )

                dispatchHandlersInPhase(dispatchHandlersWithConcurrency, unitOfWork, phase)
            }
        }

        val execute = createMiddlewareChain(finalHandler, middlewares)

        execute(event)
    }

    private fun <TEvent : Event> addErrorHandlingToDispatch(
        eventErrorStrategy: ErrorStrategy,
        message: TEvent,
        handler: EventHandler<TEvent>,
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

    private fun <TEvent : Event> dispatchHandlersWithConcurrency(
        event: TEvent,
        dispatchHandlersWithErrorHandling: List<suspend () -> Unit>,
        phase: DispatchPhase,
        eventErrorStrategy: ErrorStrategy,
    ): suspend () -> Unit = {
        if (event is DispatchSequentially) {
            dispatchHandlersWithErrorHandling.forEach { dispatchHandler -> dispatchHandler() }
        } else {
            dispatchConcurrently(phase, eventErrorStrategy, dispatchHandlersWithErrorHandling)
        }
    }

    private suspend fun dispatchConcurrently(
        phase: DispatchPhase,
        eventErrorStrategy: ErrorStrategy,
        handlerDispatchFunctions: List<suspend () -> Unit>,
    ) {
        if (
            phase === DispatchPhase.POST_COMMIT &&
                eventErrorStrategy === ErrorStrategy.FIRE_AND_FORGET
        ) {
            handlerDispatchFunctions.forEach { dispatchHandler ->
                dispatcherScope.launch { dispatchHandler() }
            }
        } else {
            coroutineScope {
                handlerDispatchFunctions
                    .map { dispatchHandler -> async { dispatchHandler() } }
                    .awaitAll()
            }
        }
    }

    private suspend fun dispatchHandlersInPhase(
        dispatch: suspend () -> Unit,
        unitOfWork: UnitOfWork<*>,
        phase: DispatchPhase,
    ) {
        when (phase) {
            DispatchPhase.IMMEDIATE -> dispatch()
            DispatchPhase.SECONDARY -> unitOfWork.addSecondaryWork { dispatch() }
            DispatchPhase.POST_COMMIT -> unitOfWork.addPostCommitWork { dispatch() }
        }
    }

    private fun <TEvent : Event> handleFailure(
        message: TEvent,
        handler: EventHandler<TEvent>,
        e: Throwable,
    ) {
        // TODO Log the error, send to a Dead Letter Queue (DLQ)
        println(
            "Handler ${handler::class.simpleName} failed for event $message. Error: ${e.message}"
        )
    }
}

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

private fun dispatchPhaseFor(
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
            errorStrategy in listOf(ErrorStrategy.FAIL_FAST, ErrorStrategy.CONTINUE_AND_AGGREGATE)
    )
        error(
            "events with fail-fast or aggregate error strategies cannot be " +
                "dispatched outside the unit of work"
        )
}
