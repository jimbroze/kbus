package com.jimbroze.kbus.example.fixtures

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.api.result.MessageFailure

class PlaceOrder(val items: List<String>) : Command<BusResult<String, MessageFailure>>()

interface OrderRepository {
    fun save(items: List<String>): String
}

interface PaymentService {
    fun charge(orderId: String)
}
