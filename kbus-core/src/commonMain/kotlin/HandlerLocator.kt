package com.jimbroze.kbus.core

import com.jimbroze.kbus.core.domain.DomainEvent

interface MessageHandlerLocator {
    fun <TCommand : Command<TResult>, TResult : KBusResult> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TResult>?

    fun <TQuery : Query<TResult>, TResult : KBusResult> handlerFor(
        query: TQuery
    ): QueryHandler<TQuery, TResult>?

    fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>>
}

// Application layer
interface DomainEventMapper {
    fun addDomainHandlers(mappings: List<EventHandlerMapping<out DomainEvent>>)
}

// Top layer
interface IntegrationEventMapper {
    fun addEventHandlers(mappings: List<EventHandlerMapping<out IntegrationEvent>>)
}

interface InlineIntegrationEventMapper {
    fun addInlineEventHandlers(mappings: List<EventAndHandlerFactories<out IntegrationEvent>>)

    fun removeInlineEventHandlers(mappings: List<EventAndHandlerFactories<out IntegrationEvent>>)
}

interface EventMapperProvider {
    val domainEventMapper: DomainEventMapper
    val integrationEventMapper: IntegrationEventMapper
    val inlineIntegrationEventMapper: InlineIntegrationEventMapper
}
