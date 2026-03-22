// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleDomainEvents02

import com.jimbroze.kbus.core.messages.event.DispatchImmediatelyInTransaction
import com.jimbroze.kbus.core.messages.event.DispatchAtEndOfTransaction
import com.jimbroze.kbus.core.messages.event.DispatchAfterTransaction
import com.jimbroze.kbus.example.fixtures.OrderShipped

// Dispatched immediately when the event is raised (synchronous)
class NotifyWarehouse : DispatchImmediatelyInTransaction<OrderShipped>() {
    override suspend fun handle(message: OrderShipped) { /* ... */
    }
}

// Dispatched after the primary handler completes but before transaction commit (synchronous)
class UpdateInventory : DispatchAtEndOfTransaction<OrderShipped>() {
    override suspend fun handle(message: OrderShipped) { /* ... */
    }
}

// Dispatched after the transaction has been committed (asynchronous)
class SendShipmentNotification : DispatchAfterTransaction<OrderShipped>() {
    override suspend fun handle(message: OrderShipped) { /* ... */
    }
}
