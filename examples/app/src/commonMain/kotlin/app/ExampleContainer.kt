package com.jimbroze.kbus.example.app

import com.jimbroze.kbus.example.inventory.infrastructure.ExampleWarehouseNotifier
import com.jimbroze.kbus.example.inventory.infrastructure.InMemoryInventoryRepository
import com.jimbroze.kbus.example.orders.acl.InventoryStockReservations
import com.jimbroze.kbus.example.orders.infrastructure.ExampleEmailService
import com.jimbroze.kbus.example.orders.infrastructure.ExamplePaymentGateway
import com.jimbroze.kbus.example.orders.infrastructure.InMemoryOrderRepository
import com.jimbroze.kbus.generated.AutoLoader
import com.jimbroze.kbus.generated.CompileTimeLoadedMessageBus

/**
 * Binds every port the contexts declare to an adapter. Nothing above this module knows which
 * adapter it got, and no context module names another context's adapters.
 *
 * [bus] is supplied late because the anti-corruption layer sends through the same bus this
 * container is built for.
 */
class ExampleContainer(private val bus: () -> CompileTimeLoadedMessageBus) : AutoLoader() {
    override val orderRepository = InMemoryOrderRepository()
    override val paymentGateway = ExamplePaymentGateway()
    override val emailService = ExampleEmailService()

    override val inventoryRepository = InMemoryInventoryRepository()
    override val warehouseNotifier = ExampleWarehouseNotifier()

    override val stockReservations by lazy { InventoryStockReservations { bus().execute(it) } }
}
