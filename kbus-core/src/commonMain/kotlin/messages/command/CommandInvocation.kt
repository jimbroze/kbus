package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.api.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.core.messages.event.publish.IntegrationEventPublisherFactory
import com.jimbroze.kbus.core.uow.UnitOfWork
import com.jimbroze.kbus.core.uow.UnitOfWorkFactory

/**
 * Everything scoped to one command's execution: its [UnitOfWork] and the
 * [IntegrationEventPublisher] that applies to it. A nested command shares its caller's invocation,
 * which is how it inherits that transaction and publisher.
 */
class CommandInvocation<TResult>(
    val unitOfWork: UnitOfWork<TResult>,
    val integrationEventPublisher: IntegrationEventPublisher,
)

/**
 * Settles which publisher applies to a command once, when its invocation is created, rather than
 * re-deriving it wherever a publisher is needed.
 */
class CommandInvocationFactory(
    private val unitOfWorkFactory: UnitOfWorkFactory,
    private val integrationEventPublisherFactory: IntegrationEventPublisherFactory,
) {
    fun <TResult> create(): CommandInvocation<TResult> {
        val unitOfWork = unitOfWorkFactory.create<TResult>()
        val integrationEventPublisher = integrationEventPublisherFactory.create(unitOfWork)

        return CommandInvocation(unitOfWork, integrationEventPublisher)
    }
}
