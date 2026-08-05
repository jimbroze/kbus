package com.jimbroze.kbus.example.orders.contracts

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.FailureReason
import com.jimbroze.kbus.contracts.result.GenericFailure
import com.jimbroze.kbus.contracts.result.MessageFailure

data class OrderLine(val productId: String, val quantity: Int, val unitPrice: Double)

sealed class PlaceOrderFailure(override val reason: FailureReason) : MessageFailure {
    class OutOfStock(val productId: String) :
        PlaceOrderFailure(GenericFailure("No stock for product $productId"))

    class PaymentDeclined(val customerId: String) :
        PlaceOrderFailure(GenericFailure("Payment declined for customer $customerId"))
}

typealias PlaceOrderResult = BusResult<OrderId, PlaceOrderFailure>

class PlaceOrder(val customerId: String, val lines: List<OrderLine>, val paymentMethodId: String) :
    Command<PlaceOrderResult>()

class CancelAndReplaceOrderFailure(override val reason: FailureReason) : MessageFailure

typealias CancelAndReplaceOrderResult = BusResult<OrderId, CancelAndReplaceOrderFailure>

class CancelAndReplaceOrder(val customerId: String, val lines: List<OrderLine>) :
    Command<CancelAndReplaceOrderResult>()
