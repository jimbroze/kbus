package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.core.uow.TransactionalOutboxFactory
import com.jimbroze.kbus.core.uow.UnitOfWork

class IntegrationEventPublisherFactory(
    private val outboxFactory: TransactionalOutboxFactory,
    private val basePublisher: IntegrationEventPublisher,
) {
    fun create(unitOfWork: UnitOfWork<*>?): IntegrationEventPublisher =
        unitOfWork?.let { outboxFactory.create(it) }
            ?: outboxFactory.immediatePublisher
            ?: basePublisher
}
