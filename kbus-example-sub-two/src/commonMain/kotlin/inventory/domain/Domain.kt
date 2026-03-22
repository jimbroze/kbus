package com.jimbroze.kbus.generation.test.inventory.domain

data class StockItem(val productId: String, val quantity: Int, val warehouseId: String)

data class StockReservation(val reservationId: String, val productId: String, val quantity: Int)
