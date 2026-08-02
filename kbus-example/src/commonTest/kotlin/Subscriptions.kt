package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.core.module.EventSubscription
import com.jimbroze.kbus.core.registry.generation.subscribe
import com.jimbroze.kbus.generated.loaded
import com.jimbroze.kbus.generation.test.inventory.application.usecases.event.NotifyWarehouseHandler
import com.jimbroze.kbus.generation.test.inventory.application.usecases.event.StockReserved
import com.jimbroze.kbus.generation.test.orders.application.usecases.event.HandleOrderPlacedIntegrationHandler
import com.jimbroze.kbus.generation.test.orders.application.usecases.event.OrderPlacedIntegration

val orderSubscriptions: List<EventSubscription<*>> =
    listOf(
        subscribe(OrderPlacedIntegration::class, HandleOrderPlacedIntegrationHandler::class.loaded)
    )

val inventorySubscriptions: List<EventSubscription<*>> =
    listOf(subscribe(StockReserved::class, NotifyWarehouseHandler::class.loaded))
