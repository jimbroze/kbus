package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.core.messages.command.CommandInvocation

/**
 * The bus's single answer to "which publisher applies to this invocation?" for handlers using
 * [CanPublishIntegrationEvent][com.jimbroze.kbus.contracts.messages.event.CanPublishIntegrationEvent].
 * Returns the invocation's publisher when there is one, otherwise the base publisher.
 */
class IntegrationPublisherFactory(private val basePublisher: IntegrationEventPublisher) {
    fun publisherFor(invocation: CommandInvocation<*>?): IntegrationEventPublisher =
        invocation?.integrationEventPublisher ?: basePublisher
}
