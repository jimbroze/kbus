package com.jimbroze.kbus.example.orders.application

import com.jimbroze.kbus.example.orders.domain.Order

interface OrderRepository {
    suspend fun save(order: Order): Order

    suspend fun findById(orderId: String): Order?
}

interface PaymentGateway {
    suspend fun charge(customerId: String, amount: Double, paymentMethodId: String): Boolean
}

interface EmailService {
    suspend fun sendOrderConfirmation(orderId: String, customerId: String)
}

/**
 * Stock held for an order, in this context's terms. Whoever actually holds it is another context's
 * business, and nothing here names it.
 */
interface StockReservations {
    suspend fun reserve(productId: String, quantity: Int): Boolean
}
