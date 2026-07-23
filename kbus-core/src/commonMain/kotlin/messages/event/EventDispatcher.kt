package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.CanPublishIntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.messages.command.CommandInvocation
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareInvocationContextFactory
import com.jimbroze.kbus.core.middleware.createMiddlewareChain
import com.jimbroze.kbus.core.uow.UnitOfWork
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
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
    private val contextFactory: MiddlewareInvocationContextFactory,
) : DomainEventDispatcher {
    suspend fun <TEvent : IntegrationEvent> dispatchIntegrationEvent(
        event: TEvent,
        handlers: List<EventHandler<TEvent>> = emptyList(),
    ) {
        val errorStrategy = errorStrategyFor(event)
        val context = contextFactory.contextFor(null)
        handlers.forEach {
            (it as? CanPublishIntegrationEvent)?.setPublisher(context.integrationEventPublisher)
        }

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
        val handlers = getHandlers(event)

        val errorStrategy = errorStrategyFor(event)
        val handlersByPhase =
            handlers.groupBy { handler ->
                val domainEventHandler = handler as DomainEventHandler<*>
                domainEventHandler.setPublisher(invocation.integrationEventPublisher)
                dispatchPhaseFor(domainEventHandler).also {
                    validateDispatchPhase(it, errorStrategy)
                }
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

    private fun <TEvent : Event> dispatchHandlersWithErrorHandling(
        handlers: List<EventHandler<TEvent>>,
        message: TEvent,
        errorStrategy: EventErrorStrategy,
    ): List<suspend () -> Unit> {
        val aggregatedExceptions = mutableListOf<Exception>()

        return handlers.mapIndexed<EventHandler<TEvent>, suspend () -> Unit> { index, handler ->
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
                    }
                }

                EventErrorStrategy.FAIL_FAST -> dispatch
                EventErrorStrategy.CONTINUE_AND_AGGREGATE -> {
                    {
                        @Suppress("TooGenericExceptionCaught")
                        try {
                            dispatch()
                        } catch (e: Exception) {
                            aggregatedExceptions.add(e)
                        }

                        if (index == handlers.lastIndex && aggregatedExceptions.isNotEmpty())
                            throw MultipleException(aggregatedExceptions)
                    }
                }
            }
        }
    }

    private fun dispatchHandlersWithConcurrency(
        concurrency: EventConcurrency,
        dispatchHandlersWithErrorHandling: List<suspend () -> Unit>,
        phase: DispatchPhase?,
        errorStrategy: EventErrorStrategy,
    ): suspend () -> Unit = {
        when (concurrency) {
            EventConcurrency.SEQUENTIAL -> {
                dispatchHandlersWithErrorHandling.forEach { dispatchHandler -> dispatchHandler() }
            }
            EventConcurrency.CONCURRENT -> {
                dispatchConcurrently(phase, errorStrategy, dispatchHandlersWithErrorHandling)
            }
        }
    }

    private suspend fun dispatchConcurrently(
        phase: DispatchPhase?,
        errorStrategy: EventErrorStrategy,
        handlerDispatchFunctions: List<suspend () -> Unit>,
    ) {
        if (
            phase in listOf(null, DispatchPhase.POST_COMMIT) &&
                errorStrategy === EventErrorStrategy.FIRE_AND_FORGET
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
