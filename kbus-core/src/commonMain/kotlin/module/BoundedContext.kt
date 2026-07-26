package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.event.EventDestination
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.messages.event.dispatch.EventDispatcher
import com.jimbroze.kbus.core.registry.HandlerLocator

/**
 * The runtime object for one bounded context: it owns a slice of handlers and dispatches to them.
 *
 * A bounded context is the local-dispatch kind of [EventDestination] (external transports are other
 * destinations). A bus holds one context per module identity — each with its own [handlerLocator]
 * slice — and [appliesTo] is the real subscription set derived from that slice, so a handler in one
 * context never fires for another context's event. A bus configured with no contexts holds a single
 * implicit [ModuleId.DEFAULT] context over all of its handlers.
 *
 * **Scope:** in this stage [handlerLocator] is used only for integration-event lookup
 * ([HandlerLocator.handlersFor] / [HandlerLocator.hasHandlersFor]). Commands, queries and domain
 * events still resolve through the bus's own shared locator — that is deliberate, not an oversight.
 */
class BoundedContext(
    val id: ModuleId,
    private val subscriptions: Subscriptions,
    private val handlerLocator: HandlerLocator,
    private val eventDispatcher: () -> EventDispatcher,
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
                )
        }
    }
}
