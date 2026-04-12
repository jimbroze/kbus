package com.jimbroze.kbus.generation.test.orders.infrastructure

import com.jimbroze.kbus.generation.test.orders.application.PaymentGateway

class ExamplePaymentGateway : PaymentGateway {
    override suspend fun charge(
        customerId: String,
        amount: Double,
        paymentMethodId: String,
    ): Boolean = true
}
