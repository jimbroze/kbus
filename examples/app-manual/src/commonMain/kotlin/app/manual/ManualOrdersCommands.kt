package com.jimbroze.kbus.example.app.manual

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.messages.command.NestedCommandExecutor
import com.jimbroze.kbus.example.orders.contracts.CancelAndReplaceOrder
import com.jimbroze.kbus.example.orders.contracts.CancelAndReplaceOrderResult
import com.jimbroze.kbus.example.orders.contracts.PlaceOrder
import com.jimbroze.kbus.example.orders.contracts.PlaceOrderResult
import com.jimbroze.kbus.generated.ordersApplication.OrdersCommands

/**
 * The typed view of its own context's commands that an orders handler asks for. Each function is a
 * name for one nested execution and nothing more, so a wiring that generates none can still satisfy
 * the handler by writing them out.
 */
class ManualOrdersCommands(private val nestedCommandExecutor: NestedCommandExecutor) :
    OrdersCommands {
    override suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
        command: TCommand
    ): TResult = nestedCommandExecutor.execute(command)

    override suspend fun cancelAndReplaceOrder(
        command: CancelAndReplaceOrder
    ): CancelAndReplaceOrderResult = nestedCommandExecutor.execute(command)

    override suspend fun placeOrder(command: PlaceOrder): PlaceOrderResult =
        nestedCommandExecutor.execute(command)
}
