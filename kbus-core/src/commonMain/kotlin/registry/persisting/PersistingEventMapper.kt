package com.jimbroze.kbus.core.registry.persisting

import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.registry.DomainEventMapper
import com.jimbroze.kbus.core.registry.DuplicateEventHandlerException
import com.jimbroze.kbus.core.registry.IntegrationEventMapper
import com.jimbroze.kbus.domain.event.DomainEvent
import kotlin.reflect.KClass

class PersistingEventMapper : DomainEventMapper, IntegrationEventMapper {
    private val mappings = mutableMapOf<KClass<out Event>, MutableSet<KClass<EventHandler<*>>>>()
    private var sealed = false

    /**
     * Idempotent: a mapper shared by two contexts is sealed once per context, to the same effect.
     */
    fun seal() {
        sealed = true
    }

    override fun <TEvent : DomainEvent> addDomainHandlers(
        event: KClass<TEvent>,
        handlers: List<KClass<out EventHandler<TEvent>>>,
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
     * Whether any handler is registered for [eventClass], without creating a single handler
     * instance. [DomainEvent] and [IntegrationEvent] are disjoint class hierarchies, so a lookup by
     * an integration event's class can only ever match an integration registration.
     */
    fun hasMappingFor(eventClass: KClass<out Event>): Boolean =
        mappings[eventClass]?.isNotEmpty() == true

    fun <TEvent : Event> handlerClassesFor(event: TEvent): List<KClass<EventHandler<TEvent>>> {
        @Suppress("UNCHECKED_CAST")
        return mappings[event::class]?.toList() as? List<KClass<EventHandler<TEvent>>>
            ?: emptyList()
    }
}
