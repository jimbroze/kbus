package com.jimbroze.kbus.core.registry

import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import kotlin.reflect.KClass

/** A type-safe token representing an event handler validated by the code generator. */
class LoadedEventHandler<TEvent : Event>
@GeneratedKBusApi
constructor(val handlerClass: KClass<out EventHandler<TEvent>>)
