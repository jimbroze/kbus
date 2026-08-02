package com.jimbroze.kbus.core.registry.persisting

import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.registry.DomainEventMapper
import com.jimbroze.kbus.core.registry.DuplicateEventHandlerException
import com.jimbroze.kbus.core.registry.IntegrationEventMapper
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import kotlin.reflect.KClass

class PersistingEventMapper : DomainEventMapper, IntegrationEventMapper {
    private val mappings = mutableMapOf<KClass<out Event>, MutableSet<KClass<EventHandler<*>>>>()

    override fun <TEvent : DomainEvent> addDomainHandlers(
        event: KClass<TEvent>,
        handlers: List<KClass<out DomainEventHandler<TEvent>>>,
    ) {
        addHandlerMappings(event, handlers)
    }

    override fun <TEvent : IntegrationEvent> addEventHandlers(
        event: KClass<TEvent>,
        handlers: List<KClass<out EventHandler<TEvent>>>,
    ) {
        addHandlerMappings(event, handlers)
    }

    private fun <TEvent : Event> addHandlerMappings(
        event: KClass<TEvent>,
        handlers: List<KClass<out EventHandler<TEvent>>>,
    ) {
        val handlerSet = mappings.getOrPut(event) { mutableSetOf() }

        handlers.forEach { handlerClass ->
            @Suppress("UNCHECKED_CAST") val castedHandler = handlerClass as KClass<EventHandler<*>>
            if (!handlerSet.add(castedHandler)) {
                throw DuplicateEventHandlerException(castedHandler, event)
            }
        }
    }

    /**
     * Every event class with at least one registered handler, without creating a single handler
     * instance. [DomainEvent] and [IntegrationEvent] are disjoint class hierarchies, so an
     * integration event's class can only ever match an integration registration.
     */
    fun subscribedEventTypes(): Set<KClass<out Event>> =
        mappings.filterValues { it.isNotEmpty() }.keys.toSet()

    fun <TEvent : Event> handlerClassesFor(event: TEvent): List<KClass<EventHandler<TEvent>>> {
        @Suppress("UNCHECKED_CAST")
        return mappings[event::class]?.toList() as? List<KClass<EventHandler<TEvent>>>
            ?: emptyList()
    }

    /**
     * A domain event's class can only have been registered through [addDomainHandlers], which
     * accepts nothing but [DomainEventHandler]s — so every class returned here is one.
     */
    fun <TEvent : DomainEvent> domainHandlerClassesFor(
        event: TEvent
    ): List<KClass<DomainEventHandler<TEvent>>> {
        @Suppress("UNCHECKED_CAST")
        return mappings[event::class]?.toList() as? List<KClass<DomainEventHandler<TEvent>>>
            ?: emptyList()
    }
}
