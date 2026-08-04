package com.jimbroze.kbus.example.inventory.contracts

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import kotlin.jvm.JvmInline

@JvmInline value class ReservationId(val value: String)

class ReserveStock(val productId: String, val quantity: Int) :
    Command<BusResult<ReservationId, MessageFailure>>()

data class StockLevel(val productId: String, val available: Int)

class GetStockLevel(val productId: String) : Query<BusResult<StockLevel, MessageFailure>>()

class StockReserved(val reservationId: String, val productId: String, val quantity: Int) :
    IntegrationEvent()
