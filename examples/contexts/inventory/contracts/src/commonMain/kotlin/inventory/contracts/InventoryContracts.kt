package com.jimbroze.kbus.example.inventory.contracts

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import com.jimbroze.kbus.api.messages.query.Query
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.api.result.MessageFailure
import kotlin.jvm.JvmInline

@JvmInline value class ReservationId(val value: String)

data class InsufficientStock(val productId: String, val requested: Int) : MessageFailure

typealias ReserveStockResult = BusResult<ReservationId, InsufficientStock>

class ReserveStock(val productId: String, val quantity: Int) : Command<ReserveStockResult>()

data class StockLevel(val productId: String, val available: Int)

data class ProductNotStocked(val productId: String) : MessageFailure

typealias GetStockLevelResult = BusResult<StockLevel, ProductNotStocked>

class GetStockLevel(val productId: String) : Query<GetStockLevelResult>()

class StockReserved(val reservationId: String, val productId: String, val quantity: Int) :
    IntegrationEvent()
