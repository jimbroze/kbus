package com.jimbroze.kbus.core.messages

import com.jimbroze.kbus.api.messages.event.IntegrationEventPublisher

/**
 * What any handler can be given for the duration of one message's handling. Scoped to the
 * invocation that reached the handler, never to the handler instance.
 */
interface HandlerDependencies {
    val integrationEventPublisher: IntegrationEventPublisher
}
