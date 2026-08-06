package com.jimbroze.kbus.example.orders.infrastructure

import com.jimbroze.kbus.example.orders.application.PaymentGateway

class ExamplePaymentGateway : PaymentGateway {
    override suspend fun charge(
        customerId: String,
        amount: Double,
        paymentMethodId: String,
    ): Boolean = true
}
