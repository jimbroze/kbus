package com.jimbroze.kbus.example.inventory.application.usecases.command

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.example.inventory.application.InventoryRepository
import com.jimbroze.kbus.example.inventory.application.StockValidator
import com.jimbroze.kbus.example.inventory.contracts.InsufficientStock
import com.jimbroze.kbus.example.inventory.contracts.ReservationId
import com.jimbroze.kbus.example.inventory.contracts.ReserveStock
import com.jimbroze.kbus.example.inventory.contracts.ReserveStockResult
import com.jimbroze.kbus.example.inventory.contracts.StockReserved

@LoadMessageHandler
@Suppress("unused")
class ReserveStockHandler(
    private val inventoryRepository: InventoryRepository,
    private val stockValidator: StockValidator,
    private val integrationEventPublisher: IntegrationEventPublisher,
) : CommandHandler<ReserveStock, ReserveStockResult>() {
    override suspend fun handle(message: ReserveStock): ReserveStockResult {
        if (!stockValidator.hasAvailableStock(message.productId, message.quantity)) {
            return BusResult.failure(InsufficientStock(message.productId, message.quantity))
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
