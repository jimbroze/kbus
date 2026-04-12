package com.jimbroze.kbus.core.registry

import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.domain.event.DomainEvent
import kotlin.reflect.KClass

// Application layer
interface DomainEventMapper {
    fun <TEvent : DomainEvent> addDomainHandlers(
        event: KClass<TEvent>,
        handlers: List<KClass<out EventHandler<TEvent>>>,
    )
}

// Top layer
interface IntegrationEventMapper {
    fun <TEvent : IntegrationEvent> addEventHandlers(
        event: KClass<TEvent>,
        handlers: List<KClass<out EventHandler<TEvent>>>,
    )
}

interface EventMapperProvider {
    val domainEventMapper: DomainEventMapper
    val integrationEventMapper: IntegrationEventMapper
}
