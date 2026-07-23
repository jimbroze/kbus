package com.jimbroze.kbus.core.middleware.middleware

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.messages.event.IntegrationEventMapper
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareHandler
import com.jimbroze.kbus.core.middleware.MiddlewareInvocationContext
import com.jimbroze.kbus.domain.event.DomainEvent
import kotlin.reflect.KClass

/**
 * Pairs the domain event class an [IntegrationEventMapper] maps from with the mapper itself, so the
 * two can never disagree. Create instances with [autoPublish].
 */
class AutoPublishRegistration<TDomainEvent : DomainEvent>(
    val eventClass: KClass<TDomainEvent>,
    private val mapper: IntegrationEventMapper<TDomainEvent>,
) {
    internal fun map(event: DomainEvent): IntegrationEvent {
        @Suppress("UNCHECKED_CAST")
        return mapper.fromDomainEvent(event as TDomainEvent)
    }
}

/**
 * Registers [mapper] with [AutoPublishIntegrationEvents] for the domain event [TDomainEvent].
 *
 * Pass a lambda, or a companion object that implements [IntegrationEventMapper] (in which case
 * [TDomainEvent] is inferred):
 * ```kotlin
 * autoPublish<OrderPlaced> { OrderPlacedIntegration(it.orderId) }
 * autoPublish(OrderPlacedIntegration)
 * ```
 */
inline fun <reified TDomainEvent : DomainEvent> autoPublish(
    mapper: IntegrationEventMapper<TDomainEvent>
): AutoPublishRegistration<TDomainEvent> = AutoPublishRegistration(TDomainEvent::class, mapper)

/**
 * Auto-publishes integration events for any dispatched [DomainEvent] that has a registered
 * [AutoPublishRegistration], using the [MiddlewareInvocationContext]'s
 * [integrationEventPublisher][MiddlewareInvocationContext.integrationEventPublisher], then
 * continues the chain.
 *
 * A domain event may have multiple registrations; all of its integration events are published in a
 * single [publish][com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher.publish]
 * call. Registrations match the domain event's exact class, not its subclasses.
 */
class AutoPublishIntegrationEvents(registrations: List<AutoPublishRegistration<*>>) : Middleware {
    constructor(vararg registrations: AutoPublishRegistration<*>) : this(registrations.toList())

    private val registrationsByEventClass = registrations.groupBy { it.eventClass }

    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        context: MiddlewareInvocationContext,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        if (message is DomainEvent) {
            registrationsByEventClass[message::class]?.let { registrations ->
                context.integrationEventPublisher.publish(registrations.map { it.map(message) })
            }
        }
        return nextMiddleware(message)
    }
}
