package com.jimbroze.kbus.core.registry

import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import kotlin.reflect.KClass

interface EventFactory {
    fun <TEvent : Event> create(
        eventClass: KClass<TEvent>,
        handlerClasses: List<KClass<EventHandler<TEvent>>>,
    ): List<EventHandler<TEvent>>
}
