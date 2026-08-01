package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.registry.HandlerLocator
import com.jimbroze.kbus.domain.event.DomainEvent
import kotlin.reflect.KClass

/**
 * The only surface on which a context's event handlers can be registered. Reachable only from a
 * construction-time lambda and never exposed by a built bus, so late registration is
 * unrepresentable rather than merely rejected.
 */
class ContextRegistration(handlerLocator: HandlerLocator) {
    private val domainEventMapper = handlerLocator.domainEventMapper
    private val integrationEventMapper = handlerLocator.integrationEventMapper

    fun <TEvent : DomainEvent> addDomainHandlers(
        event: KClass<TEvent>,
        handlers: List<KClass<out EventHandler<TEvent>>>,
    ) = domainEventMapper.addDomainHandlers(event, handlers)

    fun <TEvent : IntegrationEvent> addEventHandlers(
        event: KClass<TEvent>,
        handlers: List<KClass<out EventHandler<TEvent>>>,
    ) = integrationEventMapper.addEventHandlers(event, handlers)
}
