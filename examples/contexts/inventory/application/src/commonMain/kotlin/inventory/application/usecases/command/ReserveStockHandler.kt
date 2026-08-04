package com.jimbroze.kbus.example.inventory.application.usecases.command

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.GenericFailure
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.example.inventory.application.InventoryRepository
import com.jimbroze.kbus.example.inventory.application.StockValidator
import com.jimbroze.kbus.example.inventory.contracts.ReservationId
import com.jimbroze.kbus.example.inventory.contracts.ReserveStock
import com.jimbroze.kbus.example.inventory.contracts.StockReserved

@LoadMessageHandler
@Suppress("unused")
class ReserveStockHandler(
    private val inventoryRepository: InventoryRepository,
    private val stockValidator: StockValidator,
    private val integrationEventPublisher: IntegrationEventPublisher,
) : CommandHandler<ReserveStock, BusResult<ReservationId, MessageFailure>>() {
    override suspend fun handle(message: ReserveStock): BusResult<ReservationId, MessageFailure> {
        if (!stockValidator.hasAvailableStock(message.productId, message.quantity)) {
            return BusResult.failure(
                object : MessageFailure {
                    override val reason =
                        GenericFailure("Insufficient stock for product ${message.productId}")
                }
            )
        }

        val reservation = inventoryRepository.reserve(message.productId, message.quantity)

        integrationEventPublisher.publish(
            listOf(
                StockReserved(
                    reservationId = reservation.reservationId,
                    productId = reservation.productId,
                    quantity = reservation.quantity,
                )
            )
        )

        return BusResult.success(ReservationId(reservation.reservationId))
    }
}
