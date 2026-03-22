package com.jimbroze.kbus.generation.test.orders.infrastructure

import com.jimbroze.kbus.generation.test.orders.application.OrderRepository
import com.jimbroze.kbus.generation.test.orders.domain.Order

class InMemoryOrderRepository : OrderRepository {
    private val orders = mutableMapOf<String, Order>()

    override suspend fun save(order: Order): Order {
        orders[order.id] = order
        return order
    }

    override suspend fun findById(orderId: String): Order? = orders[orderId]
}
