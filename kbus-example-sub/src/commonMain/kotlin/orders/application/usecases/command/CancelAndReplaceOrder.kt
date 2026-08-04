package com.jimbroze.kbus.generation.test.orders.application.usecases.command

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.generated.kbusExampleSub.OrdersCommands
import com.jimbroze.kbus.generation.test.orders.domain.Order
import com.jimbroze.kbus.generation.test.orders.domain.OrderItem

class CancelAndReplaceOrder(val customerId: String, val items: List<OrderItem>) :
    Command<BusResult<Order, MessageFailure>>()

@LoadMessageHandler
@Suppress("unused")
class CancelAndReplaceOrderHandler(private val ordersCommands: OrdersCommands) :
    CommandHandler<CancelAndReplaceOrder, BusResult<Order, MessageFailure>>() {
    override suspend fun handle(message: CancelAndReplaceOrder): BusResult<Order, MessageFailure> =
        ordersCommands.placeOrder(PlaceOrder(message.customerId, message.items, "stored-card"))
}
