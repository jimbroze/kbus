package com.jimbroze.kbus.core.registry

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.domain.event.DomainEvent
import kotlin.reflect.KClass

class CompileTimeDomainEventMapper(val rawMapper: DomainEventMapper) {
    fun <TEvent : DomainEvent> addDomainHandlers(
        event: KClass<TEvent>,
        handlers: List<LoadedEventHandler<TEvent>>,
    ) {
        rawMapper.addDomainHandlers(event, handlers.map { it.handlerClass })
    }
}

// TODO Ensure this is in examples
// TODO ensure that only contracts dependency in sub modules.
class CompileTimeIntegrationEventMapper(val rawMapper: IntegrationEventMapper) {
    fun <TEvent : IntegrationEvent> addEventHandlers(
        event: KClass<TEvent>,
        handlers: List<LoadedEventHandler<TEvent>>,
    ) {
        rawMapper.addEventHandlers(event, handlers.map { it.handlerClass })
    }
}
