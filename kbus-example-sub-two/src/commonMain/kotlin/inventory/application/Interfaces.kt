package com.jimbroze.kbus.generation.test.inventory.application

import com.jimbroze.kbus.generation.test.inventory.domain.StockItem
import com.jimbroze.kbus.generation.test.inventory.domain.StockReservation

interface InventoryRepository {
    suspend fun findByProductId(productId: String): StockItem?

    suspend fun reserve(productId: String, quantity: Int): StockReservation
}

class StockValidator(private val inventoryRepository: InventoryRepository) {
    suspend fun hasAvailableStock(productId: String, quantity: Int): Boolean {
        val stockItem = inventoryRepository.findByProductId(productId)
        return stockItem != null && stockItem.quantity >= quantity
    }
}

interface WarehouseNotifier {
    suspend fun notifyReservation(reservation: StockReservation)
}
