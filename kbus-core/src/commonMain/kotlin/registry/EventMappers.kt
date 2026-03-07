package com.jimbroze.kbus.core.registry

import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.domain.DomainEvent
import kotlin.reflect.KClass

// Application layer
interface DomainEventMapper {
    fun addDomainHandlers(mappings: List<EventHandlerMapping<out DomainEvent>>)
}

// Top layer
interface IntegrationEventMapper {
    fun addEventHandlers(mappings: List<EventHandlerMapping<out IntegrationEvent>>)
}

interface InlineIntegrationEventMapper {
    fun addInlineEventHandlers(mappings: List<EventAndHandlerFactories<out IntegrationEvent>>)

    fun removeInlineEventHandlers(mappings: List<EventAndHandlerFactories<out IntegrationEvent>>)
}

interface EventMapperProvider {
    val domainEventMapper: DomainEventMapper
    val integrationEventMapper: IntegrationEventMapper
    val inlineIntegrationEventMapper: InlineIntegrationEventMapper
}

data class EventAndHandlerFactories<TEvent : Event>(
    val event: KClass<TEvent>,
    val factories: List<EventHandlerFactory<TEvent, *>>,
)

data class EventHandlerMapping<TEvent : Event>(
    val event: KClass<TEvent>,
    val handlers: List<KClass<out EventHandler<TEvent>>>,
)
