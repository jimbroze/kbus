package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.event.ErrorStrategy
import com.jimbroze.kbus.contracts.messages.event.EventDestination
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.messages.event.dispatch.EventDispatcher
import com.jimbroze.kbus.core.registry.HandlerLocator

/**
 * The bus-derived runtime for one [BoundedContext]: it owns the wired event dispatcher and
 * dispatches to the context's handler slice. Built by the bus once its middleware, scope and
 * dependency wiring exist — a [BoundedContext] cannot hold this itself, since none of that is
 * available at the point a user constructs one.
 *
 * A [BoundedContext] is the local-dispatch kind of [EventDestination] (external transports are
 * other destinations). A bus holds one runtime per identity — each with its own [handlerLocator]
 * slice — and [appliesTo] is the real subscription set derived from that slice, so a handler in one
 * context never fires for another context's event.
 */
internal class ContextRuntime(
    val context: BoundedContext,
    private val subscriptions: Subscriptions,
    private val handlerLocator: HandlerLocator,
    /**
     * The bus constructs its dispatcher after the destinations it routes to (the dispatcher's
     * `contextFactory` transitively depends on the router, which depends on these runtimes), so
     * this is resolved on first [deliver], not at construction.
     */
    private val eventDispatcher: () -> EventDispatcher,
    private val ackStrategyOverride: ((ErrorStrategy) -> ErrorStrategy)? = null,
) : EventDestination {
    override val name: String
        get() = context.id.value

    override fun appliesTo(event: IntegrationEvent): Boolean = subscriptions.contains(event)

    override suspend fun deliver(envelopes: List<EventEnvelope>) {
        envelopes.forEach { envelope ->
            eventDispatcher()
                .dispatchIntegrationEvent(
                    envelope.event,
                    handlerLocator.handlersFor(envelope.event),
                    ackStrategyOverride?.invoke(envelope.event.errorStrategy),
                )
        }
    }

    /**
     * Returns a copy overridden by [override] — an ack policy's mapping from an event's own
     * [ErrorStrategy] to the one dispatch should actually use, or `null` to honour the event's
     * strategy unchanged. Internal: only [com.jimbroze.kbus.core.module.inbox.InboxCoordinator]
     * applies this, when wrapping a context with a configured inbox store — a context with no inbox
     * is never overridden.
     */
    internal fun withAckStrategy(override: ((ErrorStrategy) -> ErrorStrategy)?): ContextRuntime =
        ContextRuntime(context, subscriptions, handlerLocator, eventDispatcher, override)
}
