package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.core.messages.event.publish.IntegrationEventPublisherFactory
import com.jimbroze.kbus.core.module.BoundedContextId
import com.jimbroze.kbus.core.uow.UnitOfWork
import com.jimbroze.kbus.core.uow.UnitOfWorkFactory

/**
 * The per-command scope: a command's [UnitOfWork], the [IntegrationEventPublisher] that applies to
 * it, and the [BoundedContextId] of the context that owns it — [contextId] is what lets domain
 * dispatch resolve the owning context's own domain handlers rather than some other context's.
 * Occupies the parameter slots `UnitOfWork<*>` used to occupy — threading it through the
 * dispatcher/context/dependency factories costs nothing extra.
 */
class CommandInvocation<TResult>(
    val unitOfWork: UnitOfWork<TResult>,
    val integrationEventPublisher: IntegrationEventPublisher,
    val contextId: BoundedContextId,
)

/**
 * Bus-owned: answers "which publisher applies to this command?" once, at invocation creation time,
 * instead of re-deriving it from the unit of work wherever it's needed. Delegates to
 * [integrationEventPublisherFactory], which decides whether the outbox or the base publisher
 * applies and, when it's the outbox, self-wires its flush/drain into the unit of work — this
 * factory doesn't touch the unit of work's phase API itself. [contextId] is required rather than
 * defaulted: a caller that forgets to thread the owning context would otherwise get silently wrong
 * domain dispatch instead of a compile error.
 */
class CommandInvocationFactory(
    private val unitOfWorkFactory: UnitOfWorkFactory,
    private val integrationEventPublisherFactory: IntegrationEventPublisherFactory,
) {
    fun <TResult> create(contextId: BoundedContextId): CommandInvocation<TResult> {
        val unitOfWork = unitOfWorkFactory.create<TResult>()
        val integrationEventPublisher = integrationEventPublisherFactory.create(unitOfWork)

        return CommandInvocation(unitOfWork, integrationEventPublisher, contextId)
    }
}
