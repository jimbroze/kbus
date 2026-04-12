package com.jimbroze.kbus.generation.test.orders.application

import com.jimbroze.kbus.generation.test.orders.domain.Order
import com.test.external.ExternalInterface

interface OrderRepository {
    suspend fun save(order: Order): Order

    suspend fun findById(orderId: String): Order?
}

interface PaymentGateway : ExternalInterface {
    suspend fun charge(customerId: String, amount: Double, paymentMethodId: String): Boolean
}

interface EmailService {
    suspend fun sendOrderConfirmation(orderId: String, customerId: String)
}
