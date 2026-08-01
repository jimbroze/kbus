package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.ErrorStrategy
import com.jimbroze.kbus.contracts.messages.event.EventDestination
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.messages.command.CommandInvocation
import com.jimbroze.kbus.core.messages.event.dispatch.DomainEventDispatcher
import com.jimbroze.kbus.core.messages.event.dispatch.EventDispatcher
import com.jimbroze.kbus.core.module.inbox.errorStrategyOverride
import com.jimbroze.kbus.domain.event.DomainEvent

/**
 * Delivers events to one [BoundedContext]'s handlers, and to no other context's. Both integration
 * and domain dispatch for the context go through here, so a handler registered on one context never
 * fires for another's event.
 *
 * The bus derives this once its own middleware, scope and dependency wiring exist, which is why a
 * [BoundedContext] cannot hold it: none of that wiring is available where a user declares one.
 */
internal class ContextRuntime(
    val context: BoundedContext,
    /**
     * Resolved on first delivery rather than at construction, because a dispatcher cannot be built
     * until the destinations it eventually routes to exist. [Lazy] so both integration and domain
     * dispatch share the one instance, whichever reaches it first.
     */
    private val eventDispatcher: Lazy<EventDispatcher>,
) : EventDestination, DomainEventDispatcher, CommandOwner {
    override val domainEventDispatcher: DomainEventDispatcher
        get() = this

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
                context.handlerLocator.handlersFor(envelope.event),
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
