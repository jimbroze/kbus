// This file was automatically generated from README.md by Knit tool. Do not edit.
package com.jimbroze.kbus.example.samples.exampleDomainEvents02

import com.jimbroze.kbus.domain.event.DispatchTiming
import com.jimbroze.kbus.domain.event.DomainEventHandler
import com.jimbroze.kbus.example.fixtures.OrderShipped

// Dispatched immediately when the event is raised (synchronous)
class NotifyWarehouse : DomainEventHandler<OrderShipped>() {
    override val dispatchTiming = DispatchTiming.ImmediatelyInTransaction

    override suspend fun handle(message: OrderShipped) {
        /* ... */
    }
}

// Dispatched after the primary handler completes but before transaction commit (synchronous)
class UpdateInventory : DomainEventHandler<OrderShipped>() {
    override val dispatchTiming = DispatchTiming.AtEndOfTransaction

    override suspend fun handle(message: OrderShipped) {
        /* ... */
    }
}

// Dispatched after the transaction has been committed (asynchronous)
class SendShipmentNotification : DomainEventHandler<OrderShipped>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: OrderShipped) {
        /* ... */
    }
}
