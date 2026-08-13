package com.jimbroze.kbus.core.boundedcontext

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.api.messages.event.ErrorStrategy
import com.jimbroze.kbus.api.messages.event.EventDestination
import com.jimbroze.kbus.api.messages.event.EventEnvelope
import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import com.jimbroze.kbus.api.result.KBusResult
import com.jimbroze.kbus.core.boundedcontext.inbox.errorStrategyOverride
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.messages.command.CommandInvocation
import com.jimbroze.kbus.core.messages.command.NestedCommandExecutor
import com.jimbroze.kbus.core.messages.event.dispatch.DomainEventDispatcher
import com.jimbroze.kbus.core.messages.event.dispatch.EventDispatcher
import com.jimbroze.kbus.domain.event.DomainEvent

/**
 * Delivers events to one [BoundedContext]'s handlers, and to no other context's. Both integration
 * and domain dispatch for the context go through here, so a handler registered on one context never
 * fires for another's event.
 */
internal class ContextRuntime(
    val context: BoundedContext,
    /**
     * Resolved on first delivery: a dispatcher cannot be built until the destinations it routes to
     * exist. Shared by integration and domain dispatch, whichever reaches it first.
     */
    private val eventDispatcher: Lazy<EventDispatcher>,
) : EventDestination, DomainEventDispatcher, CommandOwningContext<NestedCommandExecutor> {
    override val domainEventDispatcher: DomainEventDispatcher
        get() = this

    override fun typedCommands(
        nestedCommandExecutor: NestedCommandExecutor
    ): NestedCommandExecutor = nestedCommandExecutor

    /**
     * Null unless this context declares an inbox — with no durable ack there is nothing to
     * strengthen.
     */
    private val ackStrategyOverride: ((ErrorStrategy) -> ErrorStrategy)? =
        context.inbox?.ackPolicy?.errorStrategyOverride

    private val subscribedEventTypes = context.handlerLocator.subscribedEventTypes()

    override val name: String
        get() = context.id.value

    override fun appliesTo(event: IntegrationEvent): Boolean =
        subscribedEventTypes.contains(event::class)

    override suspend fun deliver(envelopes: List<EventEnvelope>) {
        envelopes.forEach { envelope ->
            eventDispatcher.value.dispatchIntegrationEvent(
                envelope.event,
                { handlerDependencies ->
                    context.handlerLocator.handlersFor(envelope.event, handlerDependencies)
                },
                ackStrategyOverride?.invoke(envelope.event.errorStrategy),
            )
        }
    }

    override fun <TCommand : Command<TResult>, TResult : KBusResult> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TResult>? =
        context.handlerLocator.handlerFor(command, commandDependencies)

    override suspend fun <TEvent : DomainEvent> dispatchDomainEvent(
        event: TEvent,
        invocation: CommandInvocation<*>,
    ) = eventDispatcher.value.dispatchDomainEvent(event, invocation)
}
