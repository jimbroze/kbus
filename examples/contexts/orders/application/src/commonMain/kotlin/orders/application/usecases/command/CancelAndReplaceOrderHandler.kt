package com.jimbroze.kbus.example.orders.application.usecases.command

import com.jimbroze.kbus.api.annotations.LoadMessageHandler
import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.example.orders.contracts.CancelAndReplaceOrder
import com.jimbroze.kbus.example.orders.contracts.CancelAndReplaceOrderFailure
import com.jimbroze.kbus.example.orders.contracts.CancelAndReplaceOrderResult
import com.jimbroze.kbus.example.orders.contracts.PlaceOrder
import com.jimbroze.kbus.generated.ordersApplication.OrdersCommands

/**
 * Reaching a sibling command through [OrdersCommands] runs it inside this command's transaction and
 * event phases. The same command sent through the bus would get its own.
 */
@LoadMessageHandler
@Suppress("unused")
class CancelAndReplaceOrderHandler(private val ordersCommands: OrdersCommands) :
    CommandHandler<CancelAndReplaceOrder, CancelAndReplaceOrderResult>() {
    override suspend fun handle(message: CancelAndReplaceOrder): CancelAndReplaceOrderResult =
        ordersCommands
            .placeOrder(PlaceOrder(message.customerId, message.lines, "stored-card"))
            .mapFailure { CancelAndReplaceOrderFailure(it.reason) }
}
