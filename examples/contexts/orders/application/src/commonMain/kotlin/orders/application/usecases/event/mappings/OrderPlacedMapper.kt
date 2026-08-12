package com.jimbroze.kbus.example.orders.application.usecases.event.mappings

import com.jimbroze.kbus.contracts.annotations.LoadEventMapper
import com.jimbroze.kbus.core.messages.event.dispatch.IntegrationEventMapper
import com.jimbroze.kbus.example.orders.contracts.OrderPlacedIntegration
import com.jimbroze.kbus.example.orders.domain.OrderPlaced

/**
 * Which of a domain event's facts become another context's business is this context's decision, so
 * the mapping lives with it — in the only layer that sees both the domain event and the contract
 * published for it. Where it is registered is the wiring's decision.
 */
@LoadEventMapper
object OrderPlacedMapper : IntegrationEventMapper<OrderPlaced> {
    override fun fromDomainEvent(event: OrderPlaced) = OrderPlacedIntegration(event.orderId)
}
