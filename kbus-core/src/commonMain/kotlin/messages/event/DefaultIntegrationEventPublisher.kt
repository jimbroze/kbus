package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.registry.HandlerLocator

class DefaultIntegrationEventPublisher(
    private val handlerLocator: HandlerLocator,
    private val eventDispatcher: EventDispatcher,
) : IntegrationEventPublisher {
    override suspend fun publish(events: List<IntegrationEvent>) {
        events.forEach { event ->
            eventDispatcher.dispatchIntegrationEvent(event, handlerLocator.handlersFor(event))
        }
    }
}

object EmptyIntegrationEventPublisher : IntegrationEventPublisher {
    override suspend fun publish(events: List<IntegrationEvent>) = Unit
}
