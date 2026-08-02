package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.registry.EventMapperProvider
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import kotlin.reflect.KClass

/**
 * One event and the handlers a context runs for it, paired at construction so the two can never be
 * re-crossed afterwards. A list of these can be star-projected without weakening anything: the
 * pairing is checked where each is built, not where the list is read.
 */
sealed class EventSubscription<TEvent : Event> {
    internal abstract fun registerOn(mappers: EventMapperProvider)
}

internal class DomainSubscription<TEvent : DomainEvent>(
    private val event: KClass<TEvent>,
    private val handlers: List<KClass<out DomainEventHandler<TEvent>>>,
) : EventSubscription<TEvent>() {
    override fun registerOn(mappers: EventMapperProvider) =
        mappers.domainEventMapper.addDomainHandlers(event, handlers)
}

internal class IntegrationSubscription<TEvent : IntegrationEvent>(
    private val event: KClass<TEvent>,
    private val handlers: List<KClass<out EventHandler<TEvent>>>,
) : EventSubscription<TEvent>() {
    override fun registerOn(mappers: EventMapperProvider) =
        mappers.integrationEventMapper.addEventHandlers(event, handlers)
}

fun <TEvent : IntegrationEvent> subscribe(
    event: KClass<TEvent>,
    vararg handlers: KClass<out EventHandler<TEvent>>,
): EventSubscription<TEvent> = IntegrationSubscription(event, handlers.toList())

fun <TEvent : DomainEvent> subscribeDomain(
    event: KClass<TEvent>,
    vararg handlers: KClass<out DomainEventHandler<TEvent>>,
): EventSubscription<TEvent> = DomainSubscription(event, handlers.toList())
