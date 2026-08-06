package com.jimbroze.kbus.example.inventory.contracts

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.FailureReason
import com.jimbroze.kbus.contracts.result.GenericFailure
import com.jimbroze.kbus.contracts.result.MessageFailure
import kotlin.jvm.JvmInline

@JvmInline value class ReservationId(val value: String)

class InsufficientStock(val productId: String, val requested: Int) : MessageFailure {
    override val reason: FailureReason =
        GenericFailure("Insufficient stock for product $productId: $requested requested")
}

typealias ReserveStockResult = BusResult<ReservationId, InsufficientStock>

class ReserveStock(val productId: String, val quantity: Int) : Command<ReserveStockResult>()

data class StockLevel(val productId: String, val available: Int)

class ProductNotStocked(val productId: String) : MessageFailure {
    override val reason: FailureReason = GenericFailure("Product not stocked: $productId")
}

typealias GetStockLevelResult = BusResult<StockLevel, ProductNotStocked>

class GetStockLevel(val productId: String) : Query<GetStockLevelResult>()

class StockReserved(val reservationId: String, val productId: String, val quantity: Int) :
    IntegrationEvent()
