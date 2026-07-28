package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.registry.CompileTimeDomainEventMapper
import com.jimbroze.kbus.core.registry.CompileTimeIntegrationEventMapper
import com.jimbroze.kbus.core.registry.EventMapperProvider
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
 * [handlerLocator] must also implement [EventMapperProvider] — every shipped [HandlerLocator] does
 * — which is what [addDomainHandlers]/[addEventHandlers] delegate to.
 */
class BoundedContext(
    val id: BoundedContextId,
    internal val handlerLocator: HandlerLocator = PersistingHandlerLocator(),
) {
    private val mapperProvider =
        requireNotNull(handlerLocator as? EventMapperProvider) {
            "BoundedContext requires a HandlerLocator that also implements EventMapperProvider " +
                "(every shipped HandlerLocator does)."
        }
    private val domainEventMapper = CompileTimeDomainEventMapper(mapperProvider.domainEventMapper)
    private val integrationEventMapper =
        CompileTimeIntegrationEventMapper(mapperProvider.integrationEventMapper)

    fun <TEvent : DomainEvent> addDomainHandlers(
        event: KClass<TEvent>,
        handlers: List<LoadedEventHandler<TEvent>>,
    ) = domainEventMapper.addDomainHandlers(event, handlers)

    fun <TEvent : IntegrationEvent> addEventHandlers(
        event: KClass<TEvent>,
        handlers: List<LoadedEventHandler<TEvent>>,
    ) = integrationEventMapper.addEventHandlers(event, handlers)
}
