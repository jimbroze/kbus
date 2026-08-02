package com.jimbroze.kbus.generation.test.orders.application.usecases.command

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.generated.kbusExampleSub.OrdersCommands
import com.jimbroze.kbus.generation.test.orders.domain.Order
import com.jimbroze.kbus.generation.test.orders.domain.OrderItem

/** Places an order for a customer whose payment details the caller does not have to repeat. */
class PlaceOrderForRegularCustomer(val customerId: String, val items: List<OrderItem>) :
    Command<BusResult<Order, MessageFailure>>()

@LoadMessageHandler
@Suppress("unused")
class PlaceOrderForRegularCustomerHandler(private val ordersCommands: OrdersCommands) :
    CommandHandler<PlaceOrderForRegularCustomer, BusResult<Order, MessageFailure>>() {
    override suspend fun handle(
        message: PlaceOrderForRegularCustomer
    ): BusResult<Order, MessageFailure> =
        ordersCommands.placeOrder(PlaceOrder(message.customerId, message.items, "stored-card"))
}
