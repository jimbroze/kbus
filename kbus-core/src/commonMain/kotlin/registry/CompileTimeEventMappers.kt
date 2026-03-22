package com.jimbroze.kbus.core.registry

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.domain.DomainEvent
import kotlin.reflect.KClass

class CompileTimeDomainEventMapper(val rawMapper: DomainEventMapper) {
    fun <TEvent : DomainEvent> addDomainHandlers(
        event: KClass<TEvent>,
        handlers: List<LoadedEventHandler<TEvent>>,
    ) {
        rawMapper.addDomainHandlers(event, handlers.map { it.handlerClass })
    }
}

// TODO add more event examples
// TODO Create another submodule. Put 'real-sounding' use-cases in both. They should include the
// external
// dependencies. Plus existing examples can stay in top module.
// TODO ensure that only contracts dependency in sub modules.
class CompileTimeIntegrationEventMapper(val rawMapper: IntegrationEventMapper) {
    fun <TEvent : IntegrationEvent> addEventHandlers(
        event: KClass<TEvent>,
        handlers: List<LoadedEventHandler<TEvent>>,
    ) {
        rawMapper.addEventHandlers(event, handlers.map { it.handlerClass })
    }
}
