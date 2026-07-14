package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.domain.event.DomainEvent

/**
 * Maps a [TDomainEvent] instance to the [IntegrationEvent] to publish for it.
 *
 * Register a mapper with the
 * [AutoPublishIntegrationEvents][com.jimbroze.kbus.core.middleware.middleware.AutoPublishIntegrationEvents]
 * middleware via [autoPublish][com.jimbroze.kbus.core.middleware.middleware.autoPublish], either as
 * a lambda or by implementing this interface on the integration event's companion object to declare
 * the domain event it is derived from.
 */
fun interface IntegrationEventMapper<TDomainEvent : DomainEvent> {
    fun fromDomainEvent(event: TDomainEvent): IntegrationEvent
}
