package com.jimbroze.kbus.core.registry

import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import kotlin.reflect.KClass

interface DomainEventRegistrar {
    fun <TEvent : DomainEvent> addDomainHandlers(
        event: KClass<TEvent>,
        handlers: List<KClass<out DomainEventHandler<TEvent>>>,
    )
}

interface IntegrationEventRegistrar {
    fun <TEvent : IntegrationEvent> addEventHandlers(
        event: KClass<TEvent>,
        handlers: List<KClass<out EventHandler<TEvent>>>,
    )
}

interface EventRegistrarProvider {
    val domainEventRegistrar: DomainEventRegistrar
    val integrationEventRegistrar: IntegrationEventRegistrar
}
