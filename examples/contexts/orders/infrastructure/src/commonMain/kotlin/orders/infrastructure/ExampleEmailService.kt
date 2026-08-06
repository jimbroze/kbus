package com.jimbroze.kbus.example.orders.infrastructure

import com.jimbroze.kbus.example.orders.application.EmailService

class ExampleEmailService : EmailService {
    override suspend fun sendOrderConfirmation(orderId: String, customerId: String) {
        // No-op
    }
}
