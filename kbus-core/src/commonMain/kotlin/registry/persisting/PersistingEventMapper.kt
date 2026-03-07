package com.jimbroze.kbus.core.registry.persisting

import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.registry.DomainEventMapper
import com.jimbroze.kbus.core.registry.EventAndHandlerFactories
import com.jimbroze.kbus.core.registry.EventFactory
import com.jimbroze.kbus.core.registry.EventHandlerMapping
import com.jimbroze.kbus.core.registry.InlineIntegrationEventMapper
import com.jimbroze.kbus.core.registry.IntegrationEventMapper
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.domain.DomainEvent
import kotlin.reflect.KClass

// TODO disallow multiple of same handler
class PersistingEventMapper(private val eventFactory: EventFactory) :
    DomainEventMapper, IntegrationEventMapper, InlineIntegrationEventMapper {
    private val mappings = mutableMapOf<KClass<out Event>, List<KClass<EventHandler<*>>>>()
    private val inlineMappings = mutableMapOf<KClass<out Event>, List<EventHandlerFactory<*, *>>>()

    override fun addDomainHandlers(mappings: List<EventHandlerMapping<out DomainEvent>>) {
        @Suppress("UNCHECKED_CAST")
        mappings.forEach { mapping ->
            this.mappings[mapping.event] = mapping.handlers as List<KClass<EventHandler<*>>>
        }
    }

    override fun addEventHandlers(mappings: List<EventHandlerMapping<out IntegrationEvent>>) {
        @Suppress("UNCHECKED_CAST")
        mappings.forEach { mapping ->
            this.mappings[mapping.event] = mapping.handlers as List<KClass<EventHandler<*>>>
        }
    }

    override fun addInlineEventHandlers(
        mappings: List<EventAndHandlerFactories<out IntegrationEvent>>
    ) {
        mappings.forEach { mapping -> this.inlineMappings[mapping.event] = mapping.factories }
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
