package com.jimbroze.kbus.core.registry.generation

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.module.ContextRegistration
import com.jimbroze.kbus.domain.event.DomainEvent
import kotlin.reflect.KClass

/**
 * Registers handlers the code generator has already validated. Passing a [LoadedEventHandler]
 * rather than a bare handler class is what makes "this handler has a generated factory" a
 * compile-time claim instead of a dispatch-time discovery.
 */
fun <TEvent : DomainEvent> ContextRegistration.addDomainHandlers(
    event: KClass<TEvent>,
    handlers: List<LoadedEventHandler<TEvent>>,
) = addDomainHandlers(event, handlers.map { it.handlerClass })

/** The integration-event equivalent of the [LoadedEventHandler] domain overload. */
fun <TEvent : IntegrationEvent> ContextRegistration.addEventHandlers(
    event: KClass<TEvent>,
    handlers: List<LoadedEventHandler<TEvent>>,
) = addEventHandlers(event, handlers.map { it.handlerClass })
