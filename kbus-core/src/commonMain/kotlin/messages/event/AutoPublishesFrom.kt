package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.domain.event.DomainEvent

/**
 * Implemented by an [IntegrationEvent] to declare the [TDomainEvent] it is derived from.
 *
 * [fromDomainEvent] maps an instance of that domain event to the integration event. The
 * [AutoPublishIntegrationEvents][com.jimbroze.kbus.core.middleware.middleware.AutoPublishIntegrationEvents]
 * middleware uses it to auto-publish the integration event whenever the domain event is dispatched.
 */
interface AutoPublishesFrom<TDomainEvent : DomainEvent> {
    fun fromDomainEvent(event: TDomainEvent): IntegrationEvent
}
