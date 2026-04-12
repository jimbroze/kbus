package com.jimbroze.kbus.generation.test.orders.infrastructure

import com.jimbroze.kbus.generation.test.orders.application.EmailService

class ExampleEmailService : EmailService {
    override suspend fun sendOrderConfirmation(orderId: String, customerId: String) {
        // No-op
    }
}
