package com.jimbroze.kbus.example.app.manual

import com.jimbroze.kbus.api.messages.command.CommandGateway
import com.jimbroze.kbus.core.bus.IMessageBus
import com.jimbroze.kbus.example.inventory.application.StockValidator
import com.jimbroze.kbus.example.inventory.contracts.ReserveStock
import com.jimbroze.kbus.example.inventory.contracts.ReserveStockResult
import com.jimbroze.kbus.example.inventory.infrastructure.ExampleWarehouseNotifier
import com.jimbroze.kbus.example.inventory.infrastructure.InMemoryInventoryRepository
import com.jimbroze.kbus.example.orders.acl.InventoryStockReservations
import com.jimbroze.kbus.example.orders.infrastructure.ExampleEmailService
import com.jimbroze.kbus.example.orders.infrastructure.ExamplePaymentGateway
import com.jimbroze.kbus.example.orders.infrastructure.InMemoryOrderRepository

/**
 * Binds every port the contexts declare to an adapter, written out rather than generated. The
 * contexts are the same ones the generated wiring assembles; only this file and the bus differ.
 *
 * [bus] is supplied late because the anti-corruption layer sends through the same bus this
 * container is built for.
 */
class ManualContainer(private val bus: () -> IMessageBus) {
    val orderRepository = InMemoryOrderRepository()
    val paymentGateway = ExamplePaymentGateway()
    val emailService = ExampleEmailService()

    val inventoryRepository = InMemoryInventoryRepository()
    val warehouseNotifier = ExampleWarehouseNotifier()
    val stockValidator = StockValidator(inventoryRepository)

    val stockReservations by lazy { InventoryStockReservations(ReserveStockGateway(bus())) }
}

/**
 * The single command the anti-corruption layer is entitled to send, narrowed to that command before
 * it reaches the layer at all. The generated wiring emits one of these per command a handler exists
 * for; here it is the wiring's own to write.
 */
private class ReserveStockGateway(private val bus: IMessageBus) :
    CommandGateway<ReserveStock, ReserveStockResult> {
    override suspend fun execute(command: ReserveStock): ReserveStockResult = bus.execute(command)
}
