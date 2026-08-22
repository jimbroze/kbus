package com.jimbroze.kbus.example.orders.contracts

import com.jimbroze.kbus.api.messages.query.Query
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.api.result.FailureReason
import com.jimbroze.kbus.api.result.GenericFailure
import com.jimbroze.kbus.api.result.MessageFailure

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
