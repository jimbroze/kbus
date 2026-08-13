// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleIntegrationEvents03

import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.messages.event.dispatch.IntegrationEventMapper
import com.jimbroze.kbus.core.middleware.AutoPublishIntegrationEvents
import com.jimbroze.kbus.core.middleware.autoPublish
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.domain.event.DomainEvent

class OrderPlaced(val orderId: String) : DomainEvent()

class OrderPlacedIntegration(val orderId: String) : IntegrationEvent()

object OrderPlacedMapper : IntegrationEventMapper<OrderPlaced> {
    override fun fromDomainEvent(event: OrderPlaced) = OrderPlacedIntegration(event.orderId)
}

class OrderPlacedAnalytics(val orderId: String) : IntegrationEvent()

val busWithAutoPublish = MessageBus(
    handlerLocator = PersistingHandlerLocator(),
    middlewares = listOf(
        AutoPublishIntegrationEvents(
            autoPublish(OrderPlacedMapper),
            autoPublish<OrderPlaced> { OrderPlacedAnalytics(it.orderId) },
        ),
    ),
)
