package com.jimbroze.kbus.core.middleware.infrastructure

import com.jimbroze.kbus.core.messages.command.CommandInvocation
import com.jimbroze.kbus.core.messages.event.publish.IntegrationEventPublisherFactory

/** The single place a [MiddlewareInvocationContext] is created. */
class MiddlewareInvocationContextFactory(
    private val publisherFactory: IntegrationEventPublisherFactory
) {
    fun contextFor(invocation: CommandInvocation<*>?): MiddlewareInvocationContext =
        object : MiddlewareInvocationContext {
            override val integrationEventPublisher =
                invocation?.integrationEventPublisher ?: publisherFactory.create(null)
        }
}
