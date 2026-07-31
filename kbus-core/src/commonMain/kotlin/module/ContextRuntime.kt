package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.event.ErrorStrategy
import com.jimbroze.kbus.contracts.messages.event.EventDestination
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.messages.command.CommandInvocation
import com.jimbroze.kbus.core.messages.event.dispatch.DomainEventDispatcher
import com.jimbroze.kbus.core.messages.event.dispatch.EventDispatcher
import com.jimbroze.kbus.core.module.inbox.errorStrategyOverride
import com.jimbroze.kbus.domain.event.DomainEvent

/**
 * The bus-derived runtime for one [BoundedContext]: it owns the wired event dispatcher and
 * dispatches to the context's handler slice. Built by the bus once its middleware, scope and
 * dependency wiring exist — a [BoundedContext] cannot hold this itself, since none of that is
 * available at the point a user constructs one.
 *
 * A [BoundedContext] is the local-dispatch kind of [EventDestination] (external transports are
 * other destinations). A bus holds one runtime per identity — each with its own
 * [BoundedContext.handlerLocator] slice — and [appliesTo] is the real subscription set derived from
 * that slice, so a handler in one context never fires for another context's event. It is also that
 * context's [DomainEventDispatcher] — a command's domain events dispatch through the owning
 * context's runtime and thus only ever reach that context's own domain handlers, mirroring
 * [appliesTo]'s isolation on the integration side.
 */
internal class ContextRuntime(
    val context: BoundedContext,
    private val subscriptions: Subscriptions = SnapshotSubscriptions(context.handlerLocator),
    /**
     * The bus constructs its dispatchers after the destinations it routes to (a dispatcher's
     * `contextFactory` transitively depends on the router, which depends on these runtimes), so
     * this is resolved on first [deliver]/[dispatchDomainEvent], not at construction. A [Lazy]
     * rather than a plain `() -> EventDispatcher` so that the one instance is shared by this
     * context's domain and integration dispatch however it is first reached.
     */
    private val eventDispatcher: Lazy<EventDispatcher>,
) : EventDestination, DomainEventDispatcher {
    /**
     * Null unless this context declares an inbox: an ack policy only has something to strengthen
     * once there is a durable ack behind it.
     */
    private val ackStrategyOverride: ((ErrorStrategy) -> ErrorStrategy)? =
        context.inbox?.ackPolicy?.errorStrategyOverride

    override val name: String
        get() = context.id.value

    override fun appliesTo(event: IntegrationEvent): Boolean = subscriptions.contains(event)

    override suspend fun deliver(envelopes: List<EventEnvelope>) {
        envelopes.forEach { envelope ->
            eventDispatcher.value.dispatchIntegrationEvent(
                envelope.event,
                context.handlerLocator.handlersFor(envelope.event),
                ackStrategyOverride?.invoke(envelope.event.errorStrategy),
            )
        }
    }

    override suspend fun <TEvent : DomainEvent> dispatchDomainEvent(
        event: TEvent,
        invocation: CommandInvocation<*>,
    ) = eventDispatcher.value.dispatchDomainEvent(event, invocation)
}
