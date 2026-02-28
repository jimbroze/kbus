package com.jimbroze.kbus.contracts.bus

import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent

abstract class CanDispatchIntegrationEvent {
    private lateinit var bus: BusAccess

    fun setBus(bus: BusAccess) {
        this.bus = bus
    }

    suspend fun <TEvent : IntegrationEvent> dispatch(event: TEvent) {
        bus.dispatch(event)
    }
}

interface BusAccess {
    suspend fun <TEvent : Event> dispatch(event: TEvent)
}
