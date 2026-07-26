package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.registry.HandlerLocator

/** What a [BoundedContext] consumes — the seam behind [BoundedContext.appliesTo]. */
fun interface Subscriptions {
    fun contains(event: IntegrationEvent): Boolean
}

/**
 * Derives a context's subscriptions from its own handler slice, **lazily** — evaluated per call, so
 * a handler registered after the context was constructed is subscribed to from that moment on. Do
 * not snapshot at construction time.
 */
class LocatorSubscriptions(private val locator: HandlerLocator) : Subscriptions {
    override fun contains(event: IntegrationEvent): Boolean = locator.hasHandlersFor(event)
}
