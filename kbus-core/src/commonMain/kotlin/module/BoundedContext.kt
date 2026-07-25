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
 * destinations). Today a bus holds a single, implicit default context wrapping all of its handlers,
 * so [appliesTo] accepts everything; later stages split this per bounded context and derive a real
 * subscription set.
 */
class BoundedContext(
    override val name: String,
    private val handlerLocator: HandlerLocator,
    private val eventDispatcher: () -> EventDispatcher,
) : EventDestination {
    override fun appliesTo(event: IntegrationEvent): Boolean = true

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
