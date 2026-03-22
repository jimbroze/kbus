package com.jimbroze.kbus.generation.test.inventory.infrastructure

import com.jimbroze.kbus.generation.test.inventory.application.InventoryRepository
import com.jimbroze.kbus.generation.test.inventory.domain.StockItem
import com.jimbroze.kbus.generation.test.inventory.domain.StockReservation

class InMemoryInventoryRepository : InventoryRepository {
    override suspend fun findByProductId(productId: String): StockItem =
        StockItem(productId, quantity = 100, warehouseId = "warehouse-1")

    override suspend fun reserve(productId: String, quantity: Int): StockReservation =
        StockReservation(reservationId = "res-1", productId = productId, quantity = quantity)
}
