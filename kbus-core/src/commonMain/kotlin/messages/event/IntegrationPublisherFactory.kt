package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.bus.BusAccess
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.uow.UnitOfWork

/**
 * The bus's single answer to "which publisher applies to this invocation?". Returns the unit of
 * work's outbox when there is one, otherwise the base publisher.
 */
class IntegrationPublisherFactory(private val basePublisher: IntegrationEventPublisher) {
    fun publisherFor(unitOfWork: UnitOfWork<*>?): IntegrationEventPublisher =
        unitOfWork?.transactionOutbox ?: basePublisher

    fun busAccessFor(unitOfWork: UnitOfWork<*>?): BusAccess =
        PublisherBusAccess(publisherFor(unitOfWork))
}

/** [BusAccess] that routes imperative `dispatch()` calls through whichever publisher applies. */
internal class PublisherBusAccess(private val publisher: IntegrationEventPublisher) : BusAccess {
    override suspend fun <TEvent : IntegrationEvent> dispatch(event: TEvent) {
        publisher.publish(listOf(event))
    }
}
