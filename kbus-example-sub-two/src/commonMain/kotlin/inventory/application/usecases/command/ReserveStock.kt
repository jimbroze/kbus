package com.jimbroze.kbus.generation.test.inventory.application.usecases.command

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.GenericFailure
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.generation.test.inventory.application.InventoryRepository
import com.jimbroze.kbus.generation.test.inventory.application.StockValidator
import com.jimbroze.kbus.generation.test.inventory.application.usecases.event.StockReserved

class ReserveStock(val productId: String, val quantity: Int) :
    Command<BusResult<String, MessageFailure>>()

// TODO improve example model
@LoadMessageHandler
@Suppress("unused")
class ReserveStockHandler(
    private val inventoryRepository: InventoryRepository,
    private val stockValidator: StockValidator,
) : CommandHandler<ReserveStock, BusResult<String, MessageFailure>>() {
    override suspend fun handle(message: ReserveStock): BusResult<String, MessageFailure> {
        val hasStock = stockValidator.hasAvailableStock(message.productId, message.quantity)
        if (!hasStock) {
            return BusResult.failure(
                object : MessageFailure {
                    override val reason =
                        GenericFailure("Insufficient stock for product ${message.productId}")
                }
            )
        }

        val reservation = inventoryRepository.reserve(message.productId, message.quantity)

        dispatch(
            StockReserved(
                reservationId = reservation.reservationId,
                productId = reservation.productId,
                quantity = reservation.quantity,
            )
        )

        return BusResult.success(reservation.reservationId)
    }
}
