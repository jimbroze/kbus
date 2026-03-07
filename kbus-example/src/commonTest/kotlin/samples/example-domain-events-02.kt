// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleDomainEvents02

import com.jimbroze.kbus.core.messages.event.DispatchImmediately
import com.jimbroze.kbus.core.messages.event.DispatchAtEndOfTransaction
import com.jimbroze.kbus.core.messages.event.DispatchAfterTransaction
import com.jimbroze.kbus.example.fixtures.OrderShipped

// Dispatched immediately when the event is raised
class NotifyWarehouse : DispatchImmediately<OrderShipped>() {
    override suspend fun handle(message: OrderShipped) { /* ... */
    }
}

// Dispatched after the primary handler completes but before transaction commit
class UpdateInventory : DispatchAtEndOfTransaction<OrderShipped>() {
    override suspend fun handle(message: OrderShipped) { /* ... */
    }
}

// Dispatched after the transaction has been committed
class SendShipmentNotification : DispatchAfterTransaction<OrderShipped>() {
    override suspend fun handle(message: OrderShipped) { /* ... */
    }
}
