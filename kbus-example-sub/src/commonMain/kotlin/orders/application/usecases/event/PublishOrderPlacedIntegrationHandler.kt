package com.jimbroze.kbus.generation.test.orders.application.usecases.event

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.core.messages.event.DispatchAfterTransaction
import com.jimbroze.kbus.generation.test.orders.domain.OrderPlaced

// TODO domain -> integration event publishing
@LoadMessageHandler
@Suppress("unused")
class PublishOrderPlacedIntegrationHandler : DispatchAfterTransaction<OrderPlaced>() {
    override suspend fun handle(message: OrderPlaced) {
        dispatch(OrderPlacedIntegration(orderId = message.orderId))
    }
}
