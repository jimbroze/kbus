package com.jimbroze.kbus.core.registry.generation

import com.jimbroze.kbus.api.messages.event.Event
import com.jimbroze.kbus.api.messages.event.EventHandler
import kotlin.reflect.KClass

/** A type-safe token representing an event handler validated by the code generator. */
class LoadedEventHandler<TEvent : Event, out THandler : EventHandler<TEvent>>
@GeneratedKBusApi
constructor(val handlerClass: KClass<out THandler>)
