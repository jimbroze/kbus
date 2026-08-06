package com.jimbroze.kbus.example.orders.contracts

import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.FailureReason
import com.jimbroze.kbus.contracts.result.GenericFailure
import com.jimbroze.kbus.contracts.result.MessageFailure

data class OrderSummary(
    val orderId: String,
    val customerId: String,
    val total: Double,
    val status: String,
)

class OrderNotFound(val orderId: String) : MessageFailure {
    override val reason: FailureReason = GenericFailure("Order not found: $orderId")
}

typealias GetOrderByIdResult = BusResult<OrderSummary, OrderNotFound>

class GetOrderById(val orderId: String) : Query<GetOrderByIdResult>()
