package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.registry.CompileTimeDomainEventMapper
import com.jimbroze.kbus.core.registry.CompileTimeIntegrationEventMapper
import com.jimbroze.kbus.core.registry.HandlerLocator
import com.jimbroze.kbus.core.registry.LoadedEventHandler
import com.jimbroze.kbus.domain.event.DomainEvent
import kotlin.reflect.KClass

/**
 * The only surface on which a context's event handlers can be registered. It is handed to a lambda
 * and never retained by anything a built bus exposes, so registration is confined to bus
 * construction by the shape of the API rather than by a runtime flag.
 *
 * Event handlers register either as bare handler classes — the hand-written path, usable without
 * code generation — or as [LoadedEventHandler] tokens, which only the generator can mint.
 */
class ContextRegistration(handlerLocator: HandlerLocator) {
    private val domainEventMapper = CompileTimeDomainEventMapper(handlerLocator.domainEventMapper)
    private val integrationEventMapper =
        CompileTimeIntegrationEventMapper(handlerLocator.integrationEventMapper)
    private val bareDomainEventMapper = handlerLocator.domainEventMapper
    private val bareIntegrationEventMapper = handlerLocator.integrationEventMapper

    fun <TEvent : DomainEvent> addDomainHandlers(
        event: KClass<TEvent>,
        handlers: List<LoadedEventHandler<TEvent>>,
    ) = domainEventMapper.addDomainHandlers(event, handlers)

    fun <TEvent : IntegrationEvent> addEventHandlers(
        event: KClass<TEvent>,
        handlers: List<LoadedEventHandler<TEvent>>,
    ) = integrationEventMapper.addEventHandlers(event, handlers)

    fun <TEvent : DomainEvent> addDomainHandlers(
        event: KClass<TEvent>,
        vararg handlers: KClass<out EventHandler<TEvent>>,
    ) = bareDomainEventMapper.addDomainHandlers(event, handlers.toList())

    fun <TEvent : IntegrationEvent> addEventHandlers(
        event: KClass<TEvent>,
        vararg handlers: KClass<out EventHandler<TEvent>>,
    ) = bareIntegrationEventMapper.addEventHandlers(event, handlers.toList())
}
