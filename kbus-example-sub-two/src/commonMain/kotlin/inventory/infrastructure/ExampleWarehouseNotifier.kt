package com.jimbroze.kbus.generation.test.inventory.infrastructure

import com.jimbroze.kbus.generation.test.inventory.application.WarehouseNotifier
import com.jimbroze.kbus.generation.test.inventory.domain.StockReservation

class ExampleWarehouseNotifier : WarehouseNotifier {
    override suspend fun notifyReservation(reservation: StockReservation) {
        // No-op
    }
}
