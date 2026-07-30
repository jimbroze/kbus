package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.module.inbox.ContextInbox
import com.jimbroze.kbus.core.registry.CompileTimeDomainEventMapper
import com.jimbroze.kbus.core.registry.CompileTimeIntegrationEventMapper
import com.jimbroze.kbus.core.registry.HandlerLocator
import com.jimbroze.kbus.core.registry.LoadedEventHandler
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.domain.event.DomainEvent
import kotlin.reflect.KClass

/**
 * An authored bounded context: a user constructs one with an [id] and registers its command, query,
 * domain-event and integration-event handlers via [addDomainHandlers]/[addEventHandlers] (command
 * and query registration goes through [handlerLocator] directly). A bus takes a list of these and
 * derives the runtime object that actually dispatches — a [BoundedContext] cannot own that itself,
 * since dispatch needs the bus's middleware, scope and dependency wiring, all constructed later.
 *
 * Declaring an [inbox] gives this context durable, independently-acked delivery of the integration
 * events it subscribes to; a context that declares none dispatches synchronously.
 *
 * Event handlers register either as bare handler classes — the hand-written path, usable without
 * code generation — or as [LoadedEventHandler] tokens, which only the generator can mint.
 */
class BoundedContext(
    val id: BoundedContextId,
    internal val handlerLocator: HandlerLocator = PersistingHandlerLocator(),
    internal val inbox: ContextInbox? = null,
) {
    /**
     * Closes this context's command and query registration. The bus calls this for every context it
     * is given. Event handlers stay registerable: the generated bus exposes its contexts only after
     * construction, so `bus.billing.addEventHandlers(...)` is registration on a live bus by design.
     */
    internal fun seal() = handlerLocator.seal()

    private val domainEventMapper = CompileTimeDomainEventMapper(handlerLocator.domainEventMapper)
    private val integrationEventMapper =
        CompileTimeIntegrationEventMapper(handlerLocator.integrationEventMapper)

    fun <TEvent : DomainEvent> addDomainHandlers(
        event: KClass<TEvent>,
        handlers: List<LoadedEventHandler<TEvent>>,
    ) = domainEventMapper.addDomainHandlers(event, handlers)

    fun <TEvent : IntegrationEvent> addEventHandlers(
        event: KClass<TEvent>,
        handlers: List<LoadedEventHandler<TEvent>>,
    ) = integrationEventMapper.addEventHandlers(event, handlers)

    fun <TEvent : DomainEvent> addDomainHandlers(
        event: KClass<TEvent>,
        vararg handlers: KClass<out EventHandler<TEvent>>,
    ) = handlerLocator.domainEventMapper.addDomainHandlers(event, handlers.toList())

    fun <TEvent : IntegrationEvent> addEventHandlers(
        event: KClass<TEvent>,
        vararg handlers: KClass<out EventHandler<TEvent>>,
    ) = handlerLocator.integrationEventMapper.addEventHandlers(event, handlers.toList())
}
