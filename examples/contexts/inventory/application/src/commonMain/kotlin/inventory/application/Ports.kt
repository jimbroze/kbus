package com.jimbroze.kbus.example.inventory.application

import com.jimbroze.kbus.example.inventory.domain.StockItem
import com.jimbroze.kbus.example.inventory.domain.StockReservation

interface InventoryRepository {
    suspend fun findByProductId(productId: String): StockItem?

    suspend fun reserve(productId: String, quantity: Int): StockReservation
}

interface WarehouseNotifier {
    suspend fun notifyReservation(reservation: StockReservation)
}

class StockValidator(private val inventoryRepository: InventoryRepository) {
    suspend fun hasAvailableStock(productId: String, quantity: Int): Boolean {
        val stockItem = inventoryRepository.findByProductId(productId)
        return stockItem != null && stockItem.quantity >= quantity
    }
}
