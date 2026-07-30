package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.core.messages.event.dispatch.DomainEventDispatcher
import com.jimbroze.kbus.core.messages.event.publish.IntegrationEventPublisherFactory
import com.jimbroze.kbus.core.uow.UnitOfWork
import com.jimbroze.kbus.core.uow.UnitOfWorkFactory

/**
 * The per-command scope: a command's [UnitOfWork], the [IntegrationEventPublisher] that applies to
 * it, and the [DomainEventDispatcher] of the context that owns it — dispatching through that
 * dispatcher is what confines a command's domain events to its own context's domain handlers.
 * Occupies the parameter slots `UnitOfWork<*>` used to occupy — threading it through the
 * dispatcher/context/dependency factories costs nothing extra.
 */
class CommandInvocation<TResult>(
    val unitOfWork: UnitOfWork<TResult>,
    val integrationEventPublisher: IntegrationEventPublisher,
    val domainEventDispatcher: DomainEventDispatcher,
)

/**
 * Bus-owned: answers "which publisher applies to this command?" once, at invocation creation time,
 * instead of re-deriving it from the unit of work wherever it's needed. Delegates to
 * [integrationEventPublisherFactory], which decides whether the outbox or the base publisher
 * applies and, when it's the outbox, self-wires its flush/drain into the unit of work — this
 * factory doesn't touch the unit of work's phase API itself. [create] takes the owning context's
 * dispatcher rather than its id: a resolved dispatcher cannot be looked up wrongly, and a caller
 * that has not resolved an owner cannot supply one at all.
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
