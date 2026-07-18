package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.core.uow.TransactionOutbox
import com.jimbroze.kbus.core.uow.UnitOfWork
import com.jimbroze.kbus.core.uow.UnitOfWorkFactory

/**
 * The per-command scope: a command's [UnitOfWork] plus the [IntegrationEventPublisher] that applies
 * to it. Occupies the parameter slots `UnitOfWork<*>` used to occupy — threading it through the
 * dispatcher/context/dependency factories costs nothing extra.
 */
class CommandInvocation<TResult>(
    val unitOfWork: UnitOfWork<TResult>,
    val integrationEventPublisher: IntegrationEventPublisher,
)

/**
 * Bus-owned: answers "which publisher applies to this command?" once, at invocation creation time,
 * instead of re-deriving it from the unit of work wherever it's needed. When an outbox is
 * configured, [outboxFactory] receives the unit of work so the [TransactionOutbox] can self-wire
 * its flush/drain into it — this factory doesn't touch the unit of work's phase API itself.
 */
class CommandInvocationFactory(
    private val unitOfWorkFactory: UnitOfWorkFactory,
    private val basePublisher: IntegrationEventPublisher,
    private val outboxFactory: ((UnitOfWork<*>) -> TransactionOutbox)? = null,
) {
    fun <TResult> create(): CommandInvocation<TResult> {
        val unitOfWork = unitOfWorkFactory.create<TResult>()
        val outbox = outboxFactory?.invoke(unitOfWork)

        return CommandInvocation(unitOfWork, outbox ?: basePublisher)
    }
}
