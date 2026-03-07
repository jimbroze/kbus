package com.jimbroze.kbus.core.registry.persisting

import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.core.registry.EventFactory
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.MessageHandlerFactoryStore
import kotlin.reflect.KClass

class PersistingEventFactory(val eventStore: MessageHandlerFactoryStore<Event>) : EventFactory {
    override fun <TEvent : Event> create(
        eventClass: KClass<TEvent>,
        handlerClasses: List<KClass<EventHandler<TEvent>>>,
    ): List<EventHandler<TEvent>> {
        val handlerFactories =
            eventStore.getHandlers(eventClass).filterIsInstance<EventHandlerFactory<TEvent, *>>()

        val factoriesByHandlerClass = handlerFactories.associateBy { it.handlerType }

        return handlerClasses.map { handlerClass ->
            factoriesByHandlerClass[handlerClass]?.create()
                ?: error("No factory found for handler class: ${handlerClass.simpleName}")
        }
    }
}
