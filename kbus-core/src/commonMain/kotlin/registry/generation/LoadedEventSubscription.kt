package com.jimbroze.kbus.core.registry.generation

import com.jimbroze.kbus.api.messages.event.EventHandler
import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.boundedcontext.DomainEventSubscription
import com.jimbroze.kbus.core.boundedcontext.IntegrationEventSubscription
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import kotlin.reflect.KClass

/**
 * Subscribes handlers the code generator has already validated. A [LoadedEventHandler] makes "this
 * handler has a generated factory" a compile-time claim rather than a dispatch-time discovery.
 */
fun <TEvent : DomainEvent> domainSubscription(
    event: KClass<TEvent>,
    vararg handlers: LoadedEventHandler<TEvent, DomainEventHandler<TEvent>>,
): DomainEventSubscription<TEvent> =
    DomainEventSubscription(event, handlers.map { it.handlerClass })

/** The integration-event equivalent of the [LoadedEventHandler] domain overload. */
fun <TEvent : IntegrationEvent> integrationSubscription(
    event: KClass<TEvent>,
    vararg handlers: LoadedEventHandler<TEvent, EventHandler<TEvent>>,
): IntegrationEventSubscription<TEvent> =
    IntegrationEventSubscription(event, handlers.map { it.handlerClass })
