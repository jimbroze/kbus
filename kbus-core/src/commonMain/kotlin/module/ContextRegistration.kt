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
 * The only surface on which a context's event handlers can be registered. Reachable only from a
 * construction-time lambda and never exposed by a built bus, so late registration is
 * unrepresentable rather than merely rejected.
 *
 * Handlers register either as bare classes — the hand-written path, usable without code generation
 * — or as [LoadedEventHandler] tokens, which only code generation can mint.
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
