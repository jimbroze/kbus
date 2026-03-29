package com.jimbroze.kbus.generation.test.orders.application.usecases.event

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.domain.DomainEventDispatchTiming
import com.jimbroze.kbus.domain.DomainEventHandler
import com.jimbroze.kbus.generation.test.orders.application.EmailService
import com.jimbroze.kbus.generation.test.orders.domain.OrderPlaced

@LoadMessageHandler
@Suppress("unused")
class SendOrderConfirmationEmailHandler(private val emailService: EmailService) :
    DomainEventHandler<OrderPlaced>() {
    override val dispatchTiming = DomainEventDispatchTiming.AfterTransaction

    override suspend fun handle(message: OrderPlaced) {
        emailService.sendOrderConfirmation(message.orderId, message.customerId)
    }
}
