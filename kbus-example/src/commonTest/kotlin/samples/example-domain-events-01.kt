// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleDomainEvents01

import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventPublisher

class OrderShipped(val orderId: String) : DomainEvent()

class Order(private val domainEventPublisher: DomainEventPublisher) {

    suspend fun place(orderId: String) {
        // Place the order...

        domainEventPublisher.publish(OrderShipped(orderId))
    }
}
