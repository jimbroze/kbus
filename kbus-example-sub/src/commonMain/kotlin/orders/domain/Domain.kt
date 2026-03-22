package com.jimbroze.kbus.generation.test.orders.domain

import com.jimbroze.kbus.domain.DomainEventPublisher

class Order(
    val id: String,
    val customerId: String,
    val items: List<OrderItem>,
    val total: Double,
    val status: OrderStatus,
    private val domainEventPublisher: DomainEventPublisher,
) {
    suspend fun place() {
        domainEventPublisher.publish(OrderPlaced(orderId = id, customerId = customerId))
    }
}

data class OrderItem(val productId: String, val quantity: Int, val unitPrice: Double)

enum class OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
}
