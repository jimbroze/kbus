package com.jimbroze.kbus.example.orders.infrastructure

import com.jimbroze.kbus.example.orders.application.OrderRepository
import com.jimbroze.kbus.example.orders.domain.Order

class InMemoryOrderRepository : OrderRepository {
    private val orders = mutableMapOf<String, Order>()

    override suspend fun save(order: Order): Order {
        orders[order.id] = order
        return order
    }

    override suspend fun findById(orderId: String): Order? = orders[orderId]
}
