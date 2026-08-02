package com.jimbroze.kbus.core.registry.generation

import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.module.DomainSubscription
import com.jimbroze.kbus.core.module.EventSubscription
import com.jimbroze.kbus.core.module.IntegrationSubscription
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import kotlin.reflect.KClass

/**
 * Subscribes handlers the code generator has already validated. Passing a [LoadedEventHandler]
 * rather than a bare handler class is what makes "this handler has a generated factory" a
 * compile-time claim instead of a dispatch-time discovery.
 */
fun <TEvent : DomainEvent> subscribeDomain(
    event: KClass<TEvent>,
    vararg handlers: LoadedEventHandler<TEvent, DomainEventHandler<TEvent>>,
): EventSubscription<TEvent> = DomainSubscription(event, handlers.map { it.handlerClass })

/** The integration-event equivalent of the [LoadedEventHandler] domain overload. */
fun <TEvent : IntegrationEvent> subscribe(
    event: KClass<TEvent>,
    vararg handlers: LoadedEventHandler<TEvent, EventHandler<TEvent>>,
): EventSubscription<TEvent> = IntegrationSubscription(event, handlers.map { it.handlerClass })
