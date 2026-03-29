package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.core.messages.event.DomainEventDispatcher
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventPublisher

class UnitOfWorkDomainEventPublisher(
    val baseDispatcher: DomainEventDispatcher?,
    val unitOfWork: UnitOfWork<*>,
) : DomainEventPublisher {
    override suspend fun publish(event: DomainEvent) {
        baseDispatcher?.dispatchDomainEvent(event, unitOfWork)
    }
}
