package com.jimbroze.kbus.generation.test.orders.application.usecases.event

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.core.messages.event.DispatchAfterTransaction
import com.jimbroze.kbus.generation.test.orders.application.EmailService
import com.jimbroze.kbus.generation.test.orders.domain.OrderPlaced

@LoadMessageHandler
@Suppress("unused")
class SendOrderConfirmationEmailHandler(private val emailService: EmailService) :
    DispatchAfterTransaction<OrderPlaced>() {
    override suspend fun handle(message: OrderPlaced) {
        emailService.sendOrderConfirmation(message.orderId, message.customerId)
    }
}
