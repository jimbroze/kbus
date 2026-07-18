package com.jimbroze.kbus.core.middleware

import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.core.messages.command.CommandInvocation

/** The single place a [MiddlewareInvocationContext] is created. */
class MiddlewareInvocationContextFactory(private val basePublisher: IntegrationEventPublisher) {
    fun contextFor(invocation: CommandInvocation<*>?): MiddlewareInvocationContext =
        object : MiddlewareInvocationContext {
            override val integrationEventPublisher =
                invocation?.integrationEventPublisher ?: basePublisher
        }
}
