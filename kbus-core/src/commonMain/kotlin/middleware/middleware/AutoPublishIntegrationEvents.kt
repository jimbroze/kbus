package com.jimbroze.kbus.core.middleware.middleware

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.core.messages.event.AutoPublishesFrom
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareHandler
import com.jimbroze.kbus.core.middleware.MiddlewareInvocationContext
import com.jimbroze.kbus.domain.event.DomainEvent
import kotlin.reflect.KClass

/**
 * Auto-publishes an integration event for any dispatched [DomainEvent] that has a registered
 * [AutoPublishesFrom] mapper, using the [MiddlewareInvocationContext]'s
 * [integrationEventPublisher][MiddlewareInvocationContext.integrationEventPublisher], then
 * continues the chain.
 *
 * [mappers] is keyed by the domain event class each mapper is derived from.
 */
class AutoPublishIntegrationEvents(
    private val mappers: Map<KClass<out DomainEvent>, AutoPublishesFrom<*>>
) : Middleware {
    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        context: MiddlewareInvocationContext,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        if (message is DomainEvent) {
            mappers[message::class]?.let { mapper ->
                @Suppress("UNCHECKED_CAST")
                val integrationEvent =
                    (mapper as AutoPublishesFrom<DomainEvent>).fromDomainEvent(message)
                context.integrationEventPublisher.publish(listOf(integrationEvent))
            }
        }
        return nextMiddleware(message)
    }
}
