package com.jimbroze.kbus.example.orders.contracts

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure

data class OrderLine(val productId: String, val quantity: Int, val unitPrice: Double)

class PlaceOrder(val customerId: String, val lines: List<OrderLine>, val paymentMethodId: String) :
    Command<BusResult<OrderId, MessageFailure>>()

class CancelAndReplaceOrder(val customerId: String, val lines: List<OrderLine>) :
    Command<BusResult<OrderId, MessageFailure>>()
