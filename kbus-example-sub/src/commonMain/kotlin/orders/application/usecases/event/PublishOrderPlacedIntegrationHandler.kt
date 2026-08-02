package com.jimbroze.kbus.generation.test.orders.application.usecases.event

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.domain.event.DispatchTiming
import com.jimbroze.kbus.domain.event.DomainEventHandler
import com.jimbroze.kbus.generation.test.orders.domain.OrderPlaced

@LoadMessageHandler
@Suppress("unused")
class PublishOrderPlacedIntegrationHandler(
    private val integrationEventPublisher: IntegrationEventPublisher
) : DomainEventHandler<OrderPlaced>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: OrderPlaced) {
        integrationEventPublisher.publish(listOf(OrderPlacedIntegration(orderId = message.orderId)))
    }
}
