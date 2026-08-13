package com.jimbroze.kbus.example.orders.acl

import com.jimbroze.kbus.api.messages.command.CommandGateway
import com.jimbroze.kbus.example.inventory.contracts.ReserveStock
import com.jimbroze.kbus.example.inventory.contracts.ReserveStockResult
import com.jimbroze.kbus.example.orders.application.StockReservations

/**
 * Translates this context's need for stock into the inventory context's published language.
 *
 * A [CommandGateway] rather than a bus: this module sits upstream of the generated bus in the
 * module graph, and stock reservation is the only thing it is entitled to ask of inventory.
 *
 * Going through the bus is what crossing the boundary costs: the reservation commits on its own,
 * and an order that later fails will not undo it. A compensating command is this context's job.
 */
class InventoryStockReservations(
    private val reserveStock: CommandGateway<ReserveStock, ReserveStockResult>
) : StockReservations {
    override suspend fun reserve(productId: String, quantity: Int): Boolean =
        reserveStock.execute(ReserveStock(productId, quantity)).getOrNull() != null
}
