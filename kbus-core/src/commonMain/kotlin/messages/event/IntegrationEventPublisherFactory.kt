package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.core.uow.TransactionOutboxFactory
import com.jimbroze.kbus.core.uow.UnitOfWork

class IntegrationEventPublisherFactory(
    private val outboxFactory: TransactionOutboxFactory,
    private val basePublisher: IntegrationEventPublisher,
) {
    fun create(unitOfWork: UnitOfWork<*>?): IntegrationEventPublisher {
        val outbox = unitOfWork?.let { outboxFactory.create(it) }

        return outbox ?: basePublisher
    }
}
