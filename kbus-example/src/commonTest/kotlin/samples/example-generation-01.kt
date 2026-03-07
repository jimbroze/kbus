// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleGeneration01

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure

class PlaceOrder(val items: List<String>) : Command<BusResult<String, MessageFailure>>()

interface OrderRepository {
    fun save(items: List<String>): String
}

interface PaymentService {
    fun charge(orderId: String)
}

@LoadMessageHandler
class PlaceOrderHandler(
    private val orderRepository: OrderRepository,
    private val paymentService: PaymentService,
) : CommandHandler<PlaceOrder, BusResult<String, MessageFailure>>() {

    override suspend fun handle(message: PlaceOrder): BusResult<String, MessageFailure> {
        val orderId = orderRepository.save(message.items)
        paymentService.charge(orderId)
        return BusResult.success(orderId)
    }
}
