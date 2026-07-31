package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.registry.HandlerLocator

/** The set of integration events a context consumes. */
fun interface Subscriptions {
    fun contains(event: IntegrationEvent): Boolean
}

/**
 * Subscriptions read once from a context's handlers, when the bus is built. Handlers registered
 * after that are not honoured, which is safe only because registration closes at construction.
 */
class SnapshotSubscriptions(locator: HandlerLocator) : Subscriptions {
    private val subscribedEventTypes = locator.subscribedEventTypes()

    override fun contains(event: IntegrationEvent): Boolean =
        subscribedEventTypes.contains(event::class)
}
