package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.module.inbox.ContextInbox
import com.jimbroze.kbus.core.registry.HandlerLocator
import com.jimbroze.kbus.domain.event.DomainEvent
import kotlin.reflect.KClass

/**
 * The only surface on which a context's event handlers and inbox can be declared. Reachable only
 * from a construction-time lambda and never exposed by a built bus, so late registration is
 * unrepresentable rather than merely rejected.
 */
class ContextRegistration(handlerLocator: HandlerLocator) {
    private val domainEventMapper = handlerLocator.domainEventMapper
    private val integrationEventMapper = handlerLocator.integrationEventMapper

    var inbox: ContextInbox? = null
        private set

    /**
     * Give this context durable, independently acknowledged delivery of the integration events it
     * subscribes to. The [inbox] must carry this context's own store instance: a store shared
     * between contexts lets one consume another's events.
     */
    fun useInbox(inbox: ContextInbox) {
        this.inbox = inbox
    }

    fun <TEvent : DomainEvent> addDomainHandlers(
        event: KClass<TEvent>,
        handlers: List<KClass<out EventHandler<TEvent>>>,
    ) = domainEventMapper.addDomainHandlers(event, handlers)

    fun <TEvent : IntegrationEvent> addEventHandlers(
        event: KClass<TEvent>,
        handlers: List<KClass<out EventHandler<TEvent>>>,
    ) = integrationEventMapper.addEventHandlers(event, handlers)
}
