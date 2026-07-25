package com.jimbroze.kbus.core.messages.event.publish

import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.core.uow.OutboxCoordinator
import com.jimbroze.kbus.core.uow.UnitOfWork

class IntegrationEventPublisherFactory(
    private val outboxCoordinator: OutboxCoordinator,
    private val directPublisher: DirectPublisher,
) {
    fun create(unitOfWork: UnitOfWork<*>?): IntegrationEventPublisher =
        unitOfWork?.let { outboxCoordinator.create(it) }
            ?: outboxCoordinator.immediatePublisher
            ?: directPublisher
}
