package com.jimbroze.kbus.example.orders.contracts

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.api.result.MessageFailure

data class OrderLine(val productId: String, val quantity: Int, val unitPrice: Double)

sealed interface PlaceOrderFailure : MessageFailure {
    data class OutOfStock(val productId: String) : PlaceOrderFailure

    data class PaymentDeclined(val customerId: String) : PlaceOrderFailure
}

typealias PlaceOrderResult = BusResult<OrderId, PlaceOrderFailure>

class PlaceOrder(val customerId: String, val lines: List<OrderLine>, val paymentMethodId: String) :
    Command<PlaceOrderResult>()

sealed interface CancelAndReplaceOrderFailure : MessageFailure {
    data class ReplacementNotPlaced(val cause: PlaceOrderFailure) : CancelAndReplaceOrderFailure
}

typealias CancelAndReplaceOrderResult = BusResult<OrderId, CancelAndReplaceOrderFailure>

class CancelAndReplaceOrder(val customerId: String, val lines: List<OrderLine>) :
    Command<CancelAndReplaceOrderResult>()
