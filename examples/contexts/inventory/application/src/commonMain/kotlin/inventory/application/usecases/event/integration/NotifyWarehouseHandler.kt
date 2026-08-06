package com.jimbroze.kbus.example.inventory.application.usecases.event.integration

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventHandler
import com.jimbroze.kbus.example.inventory.application.WarehouseNotifier
import com.jimbroze.kbus.example.inventory.contracts.StockReserved
import com.jimbroze.kbus.example.inventory.domain.StockReservation

@LoadMessageHandler
@Suppress("unused")
class NotifyWarehouseHandler(private val warehouseNotifier: WarehouseNotifier) :
    IntegrationEventHandler<StockReserved> {
    override suspend fun handle(message: StockReserved) {
        warehouseNotifier.notifyReservation(
            StockReservation(
                reservationId = message.reservationId,
                productId = message.productId,
                quantity = message.quantity,
            )
        )
        timesHandled++
    }

    companion object {
        var timesHandled = 0
    }
}
