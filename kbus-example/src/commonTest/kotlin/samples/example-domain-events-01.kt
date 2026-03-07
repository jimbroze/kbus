// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleDomainEvents01

class OrderShipped(val orderId: String) : DomainEvent()

class Order(private val domainEventPublisher: DomainEventPublisher) {

    override suspend fun place(orderId: Int): Boolean {
        // Place the order...

        domainEventPublisher.publish(OrderShipped(orderId))

        return true
    }
}
