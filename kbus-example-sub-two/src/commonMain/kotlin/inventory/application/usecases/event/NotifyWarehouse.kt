package com.jimbroze.kbus.generation.test.inventory.application.usecases.event

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventHandler
import com.jimbroze.kbus.generation.test.inventory.application.WarehouseNotifier
import com.jimbroze.kbus.generation.test.inventory.domain.StockReservation

class StockReserved(val reservationId: String, val productId: String, val quantity: Int) :
    IntegrationEvent()

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
    }
}
