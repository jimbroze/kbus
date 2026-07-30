package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.event.ErrorStrategy
import com.jimbroze.kbus.contracts.messages.event.EventDestination
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.messages.command.CommandInvocation
import com.jimbroze.kbus.core.messages.event.dispatch.DomainEventDispatcher
import com.jimbroze.kbus.core.messages.event.dispatch.EventDispatcher
import com.jimbroze.kbus.domain.event.DomainEvent

/**
 * The bus-derived runtime for one [BoundedContext]: it owns the wired event dispatcher and
 * dispatches to the context's handler slice. Built by the bus once its middleware, scope and
 * dependency wiring exist — a [BoundedContext] cannot hold this itself, since none of that is
 * available at the point a user constructs one.
 *
 * A [BoundedContext] is the local-dispatch kind of [EventDestination] (external transports are
 * other destinations). A bus holds one runtime per identity — each with its own [handlerLocator]
 * slice — and [appliesTo] is the real subscription set derived from that slice, so a handler in one
 * context never fires for another context's event. It is also that context's
 * [DomainEventDispatcher] — a command's domain events dispatch through the owning context's runtime
 * and thus only ever reach that context's own domain handlers, mirroring [appliesTo]'s isolation on
 * the integration side.
 */
internal class ContextRuntime(
    val context: BoundedContext,
    private val subscriptions: Subscriptions = LocatorSubscriptions(context.handlerLocator),
    /**
     * The bus constructs its dispatchers after the destinations it routes to (a dispatcher's
     * `contextFactory` transitively depends on the router, which depends on these runtimes), so
     * this is resolved on first [deliver]/[dispatchDomainEvent], not at construction. A [Lazy]
     * rather than a plain `() -> EventDispatcher` so that [withAckStrategy]'s copy can share it:
     * two independent lazies would each resolve to their own [EventDispatcher] the first time
     * either copy dispatched, splitting the one instance this context's domain and integration
     * dispatch must share into two.
     */
    private val eventDispatcher: Lazy<EventDispatcher>,
    private val ackStrategyOverride: ((ErrorStrategy) -> ErrorStrategy)? = null,
) : EventDestination, DomainEventDispatcher {
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

    /**
     * Returns a copy overridden by [override] — an ack policy's mapping from an event's own
     * [ErrorStrategy] to the one dispatch should actually use, or `null` to honour the event's
     * strategy unchanged. Internal: only [com.jimbroze.kbus.core.module.inbox.InboxCoordinator]
     * applies this, when wrapping a context with a configured inbox store — a context with no inbox
     * is never overridden. Shares [eventDispatcher] with the original rather than a fresh [Lazy],
     * so the copy still dispatches through the same lazily-created dispatcher instance as the
     * original (which [InboxCoordinator] discards in favour of the copy, but which is still
     * reachable elsewhere for domain dispatch).
     */
    internal fun withAckStrategy(override: ((ErrorStrategy) -> ErrorStrategy)?): ContextRuntime =
        ContextRuntime(context, subscriptions, eventDispatcher, override)
}
