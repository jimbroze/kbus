package com.jimbroze.kbus.core.registry.generation

import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import kotlin.reflect.KClass

/**
 * A type-safe token representing an event handler validated by the code generator. The handler kind
 * is carried rather than erased to [EventHandler], so a subscription can require a narrower kind
 * than the event type alone implies.
 */
class LoadedEventHandler<TEvent : Event, out THandler : EventHandler<TEvent>>
@GeneratedKBusApi
constructor(val handlerClass: KClass<out THandler>)
