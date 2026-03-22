package com.jimbroze.kbus.generation.test.orders.application.usecases.event

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventHandler

class OrderPlacedIntegration(val orderId: String) : IntegrationEvent()

@LoadMessageHandler
@Suppress("unused")
class HandleOrderPlacedIntegrationHandler : IntegrationEventHandler<OrderPlacedIntegration> {
    override suspend fun handle(message: OrderPlacedIntegration) {
        // Integration event consumed by external systems
    }
}
