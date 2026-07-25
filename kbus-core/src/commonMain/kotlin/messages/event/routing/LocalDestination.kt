package com.jimbroze.kbus.core.messages.event.routing

import com.jimbroze.kbus.contracts.messages.event.EventDestination
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.messages.event.dispatch.EventDispatcher
import com.jimbroze.kbus.core.registry.HandlerLocator

/**
 * Dispatches to this bus's own handlers. The only [EventDestination] until modules/inboxes land.
 */
class LocalDestination(
    private val handlerLocator: HandlerLocator,
    private val eventDispatcher: () -> EventDispatcher,
) : EventDestination {
    override val name: String = "local"

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
