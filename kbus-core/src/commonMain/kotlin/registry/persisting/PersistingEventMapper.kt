package com.jimbroze.kbus.core.registry.persisting

import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.registry.DomainEventMapper
import com.jimbroze.kbus.core.registry.DuplicateEventHandlerException
import com.jimbroze.kbus.core.registry.EventAndHandlerFactories
import com.jimbroze.kbus.core.registry.EventFactory
import com.jimbroze.kbus.core.registry.EventHandlerMapping
import com.jimbroze.kbus.core.registry.InlineIntegrationEventMapper
import com.jimbroze.kbus.core.registry.IntegrationEventMapper
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.domain.DomainEvent
import kotlin.collections.forEach
import kotlin.reflect.KClass

class PersistingEventMapper(private val eventFactory: EventFactory) :
    DomainEventMapper, IntegrationEventMapper, InlineIntegrationEventMapper {
    private val mappings = mutableMapOf<KClass<out Event>, List<KClass<EventHandler<*>>>>()
    private val inlineMappings = mutableMapOf<KClass<out Event>, List<EventHandlerFactory<*, *>>>()

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

    override fun addInlineEventHandlers(
        mappings: List<EventAndHandlerFactories<out IntegrationEvent>>
    ) {
        val allHandlers = mutableMapOf<KClass<out Event>, MutableSet<KClass<*>>>()
        mappings.forEach { mapping ->
            validateInlineHandlers(allHandlers, mapping)
            this.inlineMappings[mapping.event] =
                (this.inlineMappings[mapping.event] ?: emptyList()) + mapping.factories
        }
    }

    private fun validateInlineHandlers(
        allHandlers: MutableMap<KClass<out Event>, MutableSet<KClass<*>>>,
        mapping: EventAndHandlerFactories<out IntegrationEvent>,
    ) {
        val handlerSet =
            allHandlers.getOrPut(mapping.event) {
                this.inlineMappings[mapping.event]?.map { it.handlerType }?.toMutableSet()
                    ?: mutableSetOf()
            }
        mapping.factories.forEach { factory ->
            if (!handlerSet.add(factory.handlerType)) {
                throw DuplicateEventHandlerException(factory.handlerType, mapping.event)
            }
        }
    }

    override fun removeInlineEventHandlers(
        mappings: List<EventAndHandlerFactories<out IntegrationEvent>>
    ) {
        mappings.forEach { mappingToRemove ->
            val eventType = mappingToRemove.event
            val currentHandlers = this.inlineMappings[eventType] ?: return@forEach

            val updatedHandlers = currentHandlers - mappingToRemove.factories.toSet()

            if (updatedHandlers.isEmpty()) {
                this.inlineMappings.remove(eventType)
            } else {
                this.inlineMappings[eventType] = updatedHandlers
            }
        }
    }

    fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>> {
        val inlineFactoryHandlers =
            (inlineMappings[event::class]?.mapNotNull { factory ->
                @Suppress("UNCHECKED_CAST") (factory as? EventHandlerFactory<TEvent, *>)?.create()
            } ?: emptyList())

        @Suppress("UNCHECKED_CAST") val eventClass = event::class as? KClass<TEvent>
        @Suppress("UNCHECKED_CAST")
        val otherHandlerClasses = mappings[event::class] as? List<KClass<EventHandler<TEvent>>>
        val otherHandlers =
            if (otherHandlerClasses != null && eventClass != null) {
                eventFactory.create(eventClass, otherHandlerClasses)
            } else {
                emptyList()
            }

        return inlineFactoryHandlers + otherHandlers
    }
}
