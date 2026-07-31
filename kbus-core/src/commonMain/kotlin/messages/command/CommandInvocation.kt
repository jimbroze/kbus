package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.core.messages.event.dispatch.DomainEventDispatcher
import com.jimbroze.kbus.core.messages.event.publish.IntegrationEventPublisherFactory
import com.jimbroze.kbus.core.uow.UnitOfWork
import com.jimbroze.kbus.core.uow.UnitOfWorkFactory

/**
 * Everything scoped to one command's execution: its [UnitOfWork], the [IntegrationEventPublisher]
 * that applies to it, and the [DomainEventDispatcher] of the context that owns it. Dispatching
 * through the latter is what confines a command's domain events to its own context's handlers.
 */
class CommandInvocation<TResult>(
    val unitOfWork: UnitOfWork<TResult>,
    val integrationEventPublisher: IntegrationEventPublisher,
    val domainEventDispatcher: DomainEventDispatcher,
)

/**
 * Settles which publisher applies to a command once, when its invocation is created, rather than
 * re-deriving it wherever a publisher is needed.
 *
 * [create] takes the owning context's dispatcher rather than its id, so a caller that has not
 * already resolved an owner cannot supply one at all.
 */
class CommandInvocationFactory(
    private val unitOfWorkFactory: UnitOfWorkFactory,
    private val integrationEventPublisherFactory: IntegrationEventPublisherFactory,
) {
    fun <TResult> create(domainEventDispatcher: DomainEventDispatcher): CommandInvocation<TResult> {
        val unitOfWork = unitOfWorkFactory.create<TResult>()
        val integrationEventPublisher = integrationEventPublisherFactory.create(unitOfWork)

        return CommandInvocation(unitOfWork, integrationEventPublisher, domainEventDispatcher)
    }
}
