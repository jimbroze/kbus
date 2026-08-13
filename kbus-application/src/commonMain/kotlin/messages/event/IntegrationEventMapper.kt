package com.jimbroze.kbus.application.messages.event

import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import com.jimbroze.kbus.domain.event.DomainEvent

/**
 * Maps a [TDomainEvent] instance to the [IntegrationEvent] to publish for it.
 *
 * Which of a domain event's facts become another context's business is the producing context's
 * decision, so a mapper belongs with the producer — the only layer that may see both the domain
 * event and the published contract.
 *
 * Register a mapper with the
 * [AutoPublishIntegrationEvents][com.jimbroze.kbus.core.middleware.AutoPublishIntegrationEvents]
 * middleware via [autoPublish][com.jimbroze.kbus.core.middleware.autoPublish], as a lambda or as an
 * object.
 */
fun interface IntegrationEventMapper<TDomainEvent : DomainEvent> {
    fun fromDomainEvent(event: TDomainEvent): IntegrationEvent
}
