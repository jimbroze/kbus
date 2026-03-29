package com.jimbroze.kbus.generation.test.orders.application.usecases.event

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.domain.DomainEventDispatchTiming
import com.jimbroze.kbus.domain.DomainEventHandler
import com.jimbroze.kbus.generation.test.orders.domain.OrderPlaced

// TODO domain -> integration event publishing
@LoadMessageHandler
@Suppress("unused")
class PublishOrderPlacedIntegrationHandler : DomainEventHandler<OrderPlaced>() {
    override val dispatchTiming = DomainEventDispatchTiming.AfterTransaction

    override suspend fun handle(message: OrderPlaced) {
        dispatch(OrderPlacedIntegration(orderId = message.orderId))
    }
}
