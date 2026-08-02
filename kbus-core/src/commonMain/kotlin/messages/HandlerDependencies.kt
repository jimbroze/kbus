package com.jimbroze.kbus.core.messages

import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher

/**
 * What any handler can be given for the duration of one message's handling: scoped to the
 * invocation that reached it, not to the handler instance. A handler declares what it wants as a
 * constructor parameter, so two invocations running concurrently cannot see each other's.
 */
interface HandlerDependencies {
    val integrationEventPublisher: IntegrationEventPublisher
}

/** What an event handler is given: no command's invocation reached it, so nothing more exists. */
data class EventHandlerDependencies(
    override val integrationEventPublisher: IntegrationEventPublisher
) : HandlerDependencies
