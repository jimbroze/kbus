package com.jimbroze.kbus.generation.test.orders.application.usecases.event

import com.jimbroze.kbus.contracts.annotations.LoadEvent
import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventHandler
import com.jimbroze.kbus.core.messages.event.publish.AutoPublishesFrom
import com.jimbroze.kbus.generation.test.orders.domain.OrderPlaced

@LoadEvent
class OrderPlacedIntegration(val orderId: String) : IntegrationEvent() {
    companion object : AutoPublishesFrom<OrderPlaced> {
        override fun fromDomainEvent(event: OrderPlaced) = OrderPlacedIntegration(event.orderId)
    }
}

@LoadMessageHandler
@Suppress("unused")
class HandleOrderPlacedIntegrationHandler : IntegrationEventHandler<OrderPlacedIntegration> {
    override suspend fun handle(message: OrderPlacedIntegration) {
        // Integration event consumed by external systems
    }
}
