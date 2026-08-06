package com.jimbroze.kbus.example.fixtures

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure

class PlaceOrder(val items: List<String>) : Command<BusResult<String, MessageFailure>>()

interface OrderRepository {
    fun save(items: List<String>): String
}

interface PaymentService {
    fun charge(orderId: String)
}
