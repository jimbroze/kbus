package com.jimbroze.kbus.example.orders.application.usecases.event

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.domain.event.DispatchTiming
import com.jimbroze.kbus.domain.event.DomainEventHandler
import com.jimbroze.kbus.example.orders.application.EmailService
import com.jimbroze.kbus.example.orders.domain.OrderPlaced

@LoadMessageHandler
@Suppress("unused")
class SendOrderConfirmationEmailHandler(private val emailService: EmailService) :
    DomainEventHandler<OrderPlaced>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: OrderPlaced) {
        emailService.sendOrderConfirmation(message.orderId, message.customerId)
    }
}
