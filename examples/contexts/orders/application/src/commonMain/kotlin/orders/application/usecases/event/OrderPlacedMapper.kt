package com.jimbroze.kbus.example.orders.application.usecases.event

import com.jimbroze.kbus.core.messages.event.dispatch.IntegrationEventMapper
import com.jimbroze.kbus.example.orders.contracts.OrderPlacedIntegration
import com.jimbroze.kbus.example.orders.domain.OrderPlaced

/**
 * Which of a domain event's facts become another context's business is this context's decision, so
 * the mapping lives with it. Where it is registered is the wiring's decision.
 *
 * A standalone mapper rather than a companion on [OrderPlacedIntegration] because the integration
 * event is published language: it names no domain type, so nothing that consumes it inherits one.
 */
object OrderPlacedMapper : IntegrationEventMapper<OrderPlaced> {
    override fun fromDomainEvent(event: OrderPlaced) = OrderPlacedIntegration(event.orderId)
}
