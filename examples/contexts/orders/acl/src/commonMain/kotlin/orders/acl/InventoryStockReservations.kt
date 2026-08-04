package com.jimbroze.kbus.example.orders.acl

import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.example.inventory.contracts.ReservationId
import com.jimbroze.kbus.example.inventory.contracts.ReserveStock
import com.jimbroze.kbus.example.orders.application.StockReservations

/**
 * Translates this context's need for stock into the inventory context's published language.
 *
 * [sendReserveStock] rather than a bus, because typed dispatch only exists on the generated bus
 * class, which is assembled downstream of every context — taking the one call as a parameter keeps
 * that dependency pointing the way the module graph already does.
 *
 * Going through the bus is what crossing the boundary costs: the reservation commits on its own,
 * and an order that later fails will not undo it. A compensating command is this context's job.
 */
class InventoryStockReservations(
    private val sendReserveStock: suspend (ReserveStock) -> BusResult<ReservationId, MessageFailure>
) : StockReservations {
    override suspend fun reserve(productId: String, quantity: Int): Boolean =
        sendReserveStock(ReserveStock(productId, quantity)).getOrNull() != null
}
