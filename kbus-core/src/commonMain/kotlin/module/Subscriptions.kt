package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.registry.HandlerLocator

/** What a [ContextRuntime] consumes — the seam behind [ContextRuntime.appliesTo]. */
fun interface Subscriptions {
    fun contains(event: IntegrationEvent): Boolean
}

/**
 * A context's subscriptions, read once from its own handler slice when the bus is built. Safe
 * because registration is confined to construction: nothing can add a handler after this, so there
 * is no later state for a snapshot to miss.
 */
class SnapshotSubscriptions(locator: HandlerLocator) : Subscriptions {
    private val subscribedEventTypes = locator.subscribedEventTypes()

    override fun contains(event: IntegrationEvent): Boolean =
        subscribedEventTypes.contains(event::class)
}
