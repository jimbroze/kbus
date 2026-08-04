package com.jimbroze.kbus.example.inventory.infrastructure

import com.jimbroze.kbus.example.inventory.application.InventoryRepository
import com.jimbroze.kbus.example.inventory.domain.StockItem
import com.jimbroze.kbus.example.inventory.domain.StockReservation

class InMemoryInventoryRepository : InventoryRepository {
    override suspend fun findByProductId(productId: String): StockItem =
        StockItem(productId, quantity = 100, warehouseId = "warehouse-1")

    override suspend fun reserve(productId: String, quantity: Int): StockReservation =
        StockReservation(reservationId = "res-1", productId = productId, quantity = quantity)
}
