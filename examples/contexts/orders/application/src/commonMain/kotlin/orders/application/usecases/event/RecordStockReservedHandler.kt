package com.jimbroze.kbus.example.orders.application.usecases.event

import com.jimbroze.kbus.contracts.annotations.LoadMessageHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventHandler
import com.jimbroze.kbus.example.inventory.contracts.StockReserved

/**
 * Reacting to another context's event is an application use case like any other, so it sits with
 * them rather than in the anti-corruption layer, which only ever calls outwards.
 */
@LoadMessageHandler
@Suppress("unused")
class RecordStockReservedHandler : IntegrationEventHandler<StockReserved> {
    override suspend fun handle(message: StockReserved) {
        timesHandled++
    }

    companion object {
        var timesHandled = 0
    }
}
