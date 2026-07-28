package com.jimbroze.kbus.core.messages.event.publish

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.messages.event.dispatch.IntegrationEventMapper
import com.jimbroze.kbus.domain.event.DomainEvent

/**
 * Implemented by an [IntegrationEvent]'s companion object to declare the [TDomainEvent] the event
 * is derived from, and how to map it:
 * ```kotlin
 * class OrderPlacedIntegration(val orderId: String) : IntegrationEvent() {
 *     companion object : AutoPublishesFrom<OrderPlaced> {
 *         override fun fromDomainEvent(event: OrderPlaced) = OrderPlacedIntegration(event.orderId)
 *     }
 * }
 * ```
 *
 * Register the companion with the
 * [AutoPublishIntegrationEvents][com.jimbroze.kbus.core.middleware.middleware.AutoPublishIntegrationEvents]
 * middleware via [autoPublish][com.jimbroze.kbus.core.middleware.middleware.autoPublish]:
 * `autoPublish(OrderPlacedIntegration)`. Declaring the mapping on the event itself (rather than as
 * a standalone [IntegrationEventMapper]) also makes it discoverable by code generation.
 */
interface AutoPublishesFrom<TDomainEvent : DomainEvent> : IntegrationEventMapper<TDomainEvent>
