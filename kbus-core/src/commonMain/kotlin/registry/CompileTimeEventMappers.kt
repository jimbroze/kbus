package com.jimbroze.kbus.core.registry

import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.domain.DomainEvent
import kotlin.reflect.KClass

class CompileTimeDomainEventMapper(val rawMapper: DomainEventMapper) {
    fun <TEvent : DomainEvent> addDomainHandlers(
        event: KClass<TEvent>,
        handlers: List<LoadedHandlerToken<TEvent>>,
    ) {
        rawMapper.addDomainHandlers(event, handlers.map { it.handlerClass })
    }
}

class CompileTimeIntegrationEventMapper(val rawMapper: IntegrationEventMapper) {
    fun <TEvent : IntegrationEvent> addEventHandlers(
        event: KClass<TEvent>,
        handlers: List<LoadedHandlerToken<TEvent>>,
    ) {
        rawMapper.addEventHandlers(event, handlers.map { it.handlerClass })
    }
}
