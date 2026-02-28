package com.jimbroze.kbus.core.registry

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.domain.DomainEvent
import com.jimbroze.kbus.core.uow.CommandDependencies

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
