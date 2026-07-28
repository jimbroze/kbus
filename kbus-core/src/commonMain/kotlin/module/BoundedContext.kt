package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.event.ErrorStrategy
import com.jimbroze.kbus.contracts.messages.event.EventDestination
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.messages.event.dispatch.EventDispatcher
import com.jimbroze.kbus.core.registry.HandlerLocator

/**
 * The runtime object for one bounded context: it owns a slice of handlers and dispatches to them.
 *
 * A bounded context is the local-dispatch kind of [EventDestination] (external transports are other
 * destinations). A bus holds one per identity — each with its own [handlerLocator] slice — and
 * [appliesTo] is the real subscription set derived from that slice, so a handler in one context
 * never fires for another context's event. A bus configured with no contexts holds a single
 * implicit [BoundedContextId.DEFAULT] context over all of its handlers.
 *
 * **Scope:** [handlerLocator] is used only for integration-event lookup
 * ([HandlerLocator.handlersFor] / [HandlerLocator.hasHandlersFor]). Commands, queries and domain
 * events still resolve through the bus's own shared locator — that is deliberate, not an oversight.
 */
class BoundedContext(
    val id: BoundedContextId,
    private val subscriptions: Subscriptions,
    private val handlerLocator: HandlerLocator,
    private val eventDispatcher: () -> EventDispatcher,
    private val ackStrategyOverride: ((ErrorStrategy) -> ErrorStrategy)? = null,
) : EventDestination {
    override val name: String
        get() = id.value

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
     * Returns a copy overridden by [ackStrategyOverride] — an ack policy's mapping from an event's
     * own [ErrorStrategy] to the one dispatch should actually use, or `null` to honour the event's
     * strategy unchanged. Internal: only [com.jimbroze.kbus.core.module.inbox.InboxCoordinator]
     * applies this, when wrapping a context with a configured inbox store — a context with no inbox
     * is never overridden.
     */
    internal fun withAckStrategy(
        ackStrategyOverride: ((ErrorStrategy) -> ErrorStrategy)?
    ): BoundedContext =
        BoundedContext(id, subscriptions, handlerLocator, eventDispatcher, ackStrategyOverride)
}
