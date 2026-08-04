package com.jimbroze.kbus.example.orders.contracts

import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure

data class OrderSummary(
    val orderId: String,
    val customerId: String,
    val total: Double,
    val status: String,
)

class GetOrderById(val orderId: String) : Query<BusResult<OrderSummary, MessageFailure>>()
