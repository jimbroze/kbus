package com.jimbroze.kbus.example.inventory.infrastructure

import com.jimbroze.kbus.example.inventory.application.WarehouseNotifier
import com.jimbroze.kbus.example.inventory.domain.StockReservation

class ExampleWarehouseNotifier : WarehouseNotifier {
    override suspend fun notifyReservation(reservation: StockReservation) {
        // No-op
    }
}
