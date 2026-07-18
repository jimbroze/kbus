package com.jimbroze.kbus.core.middleware

import com.jimbroze.kbus.core.messages.event.IntegrationPublisherFactory
import com.jimbroze.kbus.core.uow.UnitOfWork

/** The single place a [MiddlewareInvocationContext] is created. */
class MiddlewareInvocationContextFactory(
    private val publisherFactory: IntegrationPublisherFactory
) {
    fun contextFor(unitOfWork: UnitOfWork<*>?): MiddlewareInvocationContext =
        object : MiddlewareInvocationContext {
            override val integrationEventPublisher = publisherFactory.publisherFor(unitOfWork)
        }
}
