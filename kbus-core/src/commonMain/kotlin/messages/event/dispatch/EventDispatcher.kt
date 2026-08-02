package com.jimbroze.kbus.core.messages.event.dispatch

import com.jimbroze.kbus.contracts.messages.event.ErrorStrategy
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.messages.HandlerDependencies
import com.jimbroze.kbus.core.messages.command.CommandInvocation
import com.jimbroze.kbus.core.messages.event.EventHandlerDependencies
import com.jimbroze.kbus.core.messages.event.concurrencyFor
import com.jimbroze.kbus.core.messages.event.dispatchPhaseFor
import com.jimbroze.kbus.core.messages.event.errorStrategyFor
import com.jimbroze.kbus.core.messages.event.mapErrorStrategy
import com.jimbroze.kbus.core.messages.event.routing.AggregateException
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareInvocationContextFactory
import com.jimbroze.kbus.core.middleware.createMiddlewareChain
import com.jimbroze.kbus.core.uow.UnitOfWork
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

typealias GetDomainHandlers =
    (event: DomainEvent, handlerDependencies: HandlerDependencies) -> List<
            DomainEventHandler<DomainEvent>
        >

typealias GetIntegrationHandlers<TEvent> =
    (handlerDependencies: HandlerDependencies) -> List<EventHandler<TEvent>>

class EventDispatcher(
    val getHandlers: GetDomainHandlers,
    val middlewares: List<Middleware>,
    private val dispatcherScope: CoroutineScope,
    private val contextFactory: MiddlewareInvocationContextFactory,
) : DomainEventDispatcher {
    /**
     * [errorStrategyOverride] lets a consumer (an inboxed [BoundedContext][com.jimbroze.kbus.core
     * .module.BoundedContext] via its ack policy) require stronger delivery guarantees than the
     * event declared — e.g. treating [ErrorStrategy.FireAndForget] as
     * [ErrorStrategy.ContinueAndAggregate] so a handler failure is never silently acked.
     */
    suspend fun <TEvent : IntegrationEvent> dispatchIntegrationEvent(
        event: TEvent,
        getHandlers: GetIntegrationHandlers<TEvent> = { emptyList() },
        errorStrategyOverride: ErrorStrategy? = null,
    ) {
        val errorStrategy =
            errorStrategyOverride?.let(::mapErrorStrategy) ?: errorStrategyFor(event)
        val context = contextFactory.contextFor(null)
        val handlers = getHandlers(EventHandlerDependencies(context.integrationEventPublisher))

        val finalHandler: suspend (TEvent) -> Unit = { message: TEvent ->
            val dispatchHandlersWithErrorHandling =
                dispatchHandlersWithErrorHandling(handlers, message, errorStrategy)

            dispatchHandlersWithConcurrency(
                concurrencyFor(event),
                dispatchHandlersWithErrorHandling,
                null,
                errorStrategy,
            )()
        }

        val execute = createMiddlewareChain(finalHandler, middlewares, context)
        execute(event)
    }

    override suspend fun <TEvent : DomainEvent> dispatchDomainEvent(
        event: TEvent,
        invocation: CommandInvocation<*>,
    ) {
        val handlers =
            getHandlers(event, EventHandlerDependencies(invocation.integrationEventPublisher))

        val errorStrategy = errorStrategyFor(event)
        val handlersByPhase =
            handlers.groupBy { handler ->
                dispatchPhaseFor(handler).also { validateDispatchPhase(it, errorStrategy) }
            }

        val finalHandler: suspend (DomainEvent) -> Unit = { message: DomainEvent ->
            handlersByPhase.forEach { (phase, phaseHandlers) ->
                val dispatchHandlersWithErrorHandling =
                    dispatchHandlersWithErrorHandling(phaseHandlers, message, errorStrategy)

                val dispatchHandlersWithConcurrency =
                    dispatchHandlersWithConcurrency(
                        concurrencyFor(event),
                        dispatchHandlersWithErrorHandling,
                        phase,
                        errorStrategy,
                    )
                dispatchHandlersInPhase(
                    dispatchHandlersWithConcurrency,
                    invocation.unitOfWork,
                    phase,
                )
            }
        }
        val execute =
            createMiddlewareChain(finalHandler, middlewares, contextFactory.contextFor(invocation))
        execute(event)
    }

    /**
     * Each closure returns the exception its handler threw, or null. Only
     * [EventErrorStrategy.CONTINUE_AND_AGGREGATE] ever has that value inspected; the other two
     * return null throughout so all three can share one dispatch path.
     */
    private fun <TEvent : Event> dispatchHandlersWithErrorHandling(
        handlers: List<EventHandler<TEvent>>,
        message: TEvent,
        errorStrategy: EventErrorStrategy,
    ): List<suspend () -> Exception?> {
        return handlers.map { handler ->
            val dispatch = suspend { handler.handle(message) }

            when (errorStrategy) {
                EventErrorStrategy.FIRE_AND_FORGET -> {
                    {
                        @Suppress("TooGenericExceptionCaught")
                        try {
                            dispatch()
                        } catch (e: Exception) {
                            handleFailure(message, handler, e)
                        }
                        null
                    }
                }

                EventErrorStrategy.FAIL_FAST -> {
                    {
                        dispatch()
                        null
                    }
                }

                EventErrorStrategy.CONTINUE_AND_AGGREGATE -> {
                    {
                        @Suppress("TooGenericExceptionCaught")
                        try {
                            dispatch()
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            e
                        }
                    }
                }
            }
        }
    }

    private fun dispatchHandlersWithConcurrency(
        concurrency: EventConcurrency,
        handlerDispatchFunctions: List<suspend () -> Exception?>,
        phase: DispatchPhase?,
        errorStrategy: EventErrorStrategy,
    ): suspend () -> Unit = {
        val exceptions =
            when (concurrency) {
                EventConcurrency.SEQUENTIAL ->
                    handlerDispatchFunctions.map { dispatchHandler -> dispatchHandler() }
                EventConcurrency.CONCURRENT ->
                    dispatchConcurrently(phase, errorStrategy, handlerDispatchFunctions)
            }

        if (errorStrategy == EventErrorStrategy.CONTINUE_AND_AGGREGATE) {
            val aggregated = exceptions.filterNotNull()
            if (aggregated.isNotEmpty()) throw AggregateException(aggregated)
        }
    }

    /**
     * Detaching at [DispatchPhase.POST_COMMIT] is what stops a command's return waiting on a
     * handler scheduled for after its transaction commits. Every other phase, and every integration
     * event, is awaited.
     */
    private suspend fun dispatchConcurrently(
        phase: DispatchPhase?,
        errorStrategy: EventErrorStrategy,
        handlerDispatchFunctions: List<suspend () -> Exception?>,
    ): List<Exception?> {
        return if (
            phase == DispatchPhase.POST_COMMIT &&
                errorStrategy === EventErrorStrategy.FIRE_AND_FORGET
        ) {
            handlerDispatchFunctions.forEach { dispatchHandler ->
                dispatcherScope.launch { dispatchHandler() }
            }
            emptyList()
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

internal enum class DispatchPhase {
    IMMEDIATE,
    SECONDARY,
    POST_COMMIT,
}

internal enum class EventErrorStrategy {
    FIRE_AND_FORGET,
    FAIL_FAST,
    CONTINUE_AND_AGGREGATE,
}

internal enum class EventConcurrency {
    CONCURRENT,
    SEQUENTIAL,
}

private fun validateDispatchPhase(phase: DispatchPhase, errorStrategy: EventErrorStrategy) {
    if (
        phase == DispatchPhase.POST_COMMIT &&
            errorStrategy in
                listOf(EventErrorStrategy.FAIL_FAST, EventErrorStrategy.CONTINUE_AND_AGGREGATE)
    )
        error(
            "events with fail-fast or aggregate error strategies cannot be " +
                "dispatched outside the unit of work"
        )
}
