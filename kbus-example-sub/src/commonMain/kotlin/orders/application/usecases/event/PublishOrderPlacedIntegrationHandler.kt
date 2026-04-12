package com.jimbroze.kbus.generation.test.orders.application.usecases.event

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.domain.event.DispatchTiming
import com.jimbroze.kbus.domain.event.DomainEventHandler
import com.jimbroze.kbus.generation.test.orders.domain.OrderPlaced

// TODO domain -> integration event publishing
@LoadMessageHandler
@Suppress("unused")
class PublishOrderPlacedIntegrationHandler : DomainEventHandler<OrderPlaced>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: OrderPlaced) {
        dispatch(OrderPlacedIntegration(orderId = message.orderId))
    }
}
