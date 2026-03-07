package com.jimbroze.kbus.core.registry.persisting

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.registry.DomainEventMapper
import com.jimbroze.kbus.core.registry.EventMapperProvider
import com.jimbroze.kbus.core.registry.HandlerLocator
import com.jimbroze.kbus.core.registry.InlineIntegrationEventMapper
import com.jimbroze.kbus.core.registry.IntegrationEventMapper
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.persisting.store.MessageHandlerFactoryStore
import com.jimbroze.kbus.core.registry.persisting.store.QueryHandlerFactory

class PersistingHandlerLocator(
    stores: HandlerFactoryStoreCollection = HandlerFactoryStoreCollection()
) : HandlerLocator, EventMapperProvider {
    private val commandStore: MessageHandlerFactoryStore<Command<*>> = stores.commandStore
    private val queryStore: MessageHandlerFactoryStore<Query<*>> = stores.queryStore
    private val eventMapper = PersistingEventMapper(PersistingEventFactory(stores.eventStore))
    override val domainEventMapper = eventMapper as DomainEventMapper
    override val integrationEventMapper = eventMapper as IntegrationEventMapper
    override val inlineIntegrationEventMapper = eventMapper as InlineIntegrationEventMapper

    override fun <TCommand : Command<TResult>, TResult : KBusResult> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TResult>? {
        val factory = commandStore.getHandlers(command::class).firstOrNull() ?: return null

        require(factory is CommandHandlerFactory<TCommand, *, *>) {
            "Command factory was incorrectly registered for command type ${command::class.simpleName}"
        }

        @Suppress("UNCHECKED_CAST")
        return factory.create(commandDependencies) as CommandHandler<TCommand, TResult>
    }

    override fun <TQuery : Query<TResult>, TResult : KBusResult> handlerFor(
        query: TQuery
    ): QueryHandler<TQuery, TResult>? {
        val factory = queryStore.getHandlers(query::class).firstOrNull() ?: return null

        require(factory is QueryHandlerFactory<TQuery, *, *>) {
            "Query factory was incorrectly registered for query type ${query::class.simpleName}"
        }

        @Suppress("UNCHECKED_CAST")
        return factory.create() as QueryHandler<TQuery, TResult>
    }

    override fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>> {
        return eventMapper.handlersFor(event)
    }
}
