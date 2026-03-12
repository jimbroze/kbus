package com.jimbroze.kbus.core.registry.persisting

import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.registry.DomainEventMapper
import com.jimbroze.kbus.core.registry.DuplicateEventHandlerException
import com.jimbroze.kbus.core.registry.EventFactory
import com.jimbroze.kbus.core.registry.EventHandlerMapping
import com.jimbroze.kbus.core.registry.IntegrationEventMapper
import com.jimbroze.kbus.domain.DomainEvent
import kotlin.collections.forEach
import kotlin.reflect.KClass

class PersistingEventMapper(private val eventFactory: EventFactory) :
    DomainEventMapper, IntegrationEventMapper {
    private val mappings = mutableMapOf<KClass<out Event>, List<KClass<EventHandler<*>>>>()

    override fun addDomainHandlers(mappings: List<EventHandlerMapping<out DomainEvent>>) {
        addHandlerMappings(mappings)
    }

    override fun addEventHandlers(mappings: List<EventHandlerMapping<out IntegrationEvent>>) {
        addHandlerMappings(mappings)
    }

    private fun addHandlerMappings(mappings: List<EventHandlerMapping<out Event>>) {
        val allHandlers = mutableMapOf<KClass<out Event>, MutableSet<KClass<*>>>()
        @Suppress("UNCHECKED_CAST")
        mappings.forEach { mapping ->
            val handlerSet =
                allHandlers.getOrPut(mapping.event) {
                    (this.mappings[mapping.event]?.toMutableSet() ?: mutableSetOf())
                        as MutableSet<KClass<*>>
                }
            mapping.handlers.forEach { handler ->
                if (!handlerSet.add(handler)) {
                    throw DuplicateEventHandlerException(handler, mapping.event)
                }
            }
            this.mappings[mapping.event] = handlerSet.toList() as List<KClass<EventHandler<*>>>
        }
    }

    fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>> {
        @Suppress("UNCHECKED_CAST") val eventClass = event::class as? KClass<TEvent>
        @Suppress("UNCHECKED_CAST")
        val handlerClasses = mappings[event::class] as? List<KClass<EventHandler<TEvent>>>

        return if (handlerClasses != null && eventClass != null) {
            eventFactory.create(eventClass, handlerClasses)
        } else {
            emptyList<EventHandler<TEvent>>()
        }
    }
}
