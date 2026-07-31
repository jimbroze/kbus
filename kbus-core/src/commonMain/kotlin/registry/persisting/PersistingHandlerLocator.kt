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
import com.jimbroze.kbus.core.registry.HandlerLocator
import com.jimbroze.kbus.core.registry.IntegrationEventMapper
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.persisting.store.MessageHandlerFactoryStore
import com.jimbroze.kbus.core.registry.persisting.store.QueryHandlerFactory
import kotlin.reflect.KClass

// TODO create EventLocator that combines mapper and factory?
class PersistingHandlerLocator(
    stores: HandlerFactoryStoreCollection = HandlerFactoryStoreCollection()
) : HandlerLocator {
    private val commandStore: MessageHandlerFactoryStore<Command<*>> = stores.commandStore
    private val queryStore: MessageHandlerFactoryStore<Query<*>> = stores.queryStore
    private val eventStore: MessageHandlerFactoryStore<Event> = stores.eventStore
    private val eventMapper = PersistingEventMapper()
    override val domainEventMapper = eventMapper as DomainEventMapper
    override val integrationEventMapper = eventMapper as IntegrationEventMapper

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

    override fun hasHandlersFor(event: Event): Boolean = eventMapper.hasMappingFor(event::class)

    override fun hasHandlerFor(command: Command<*>): Boolean =
        commandStore.isRegistered(command::class)

    override fun hasHandlerFor(query: Query<*>): Boolean = queryStore.isRegistered(query::class)

    override fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>> {
        val handlerClasses = eventMapper.handlerClassesFor(event)
        if (handlerClasses.isEmpty()) return emptyList()
        @Suppress("UNCHECKED_CAST") val eventClass = event::class as KClass<TEvent>

        val handlerFactories =
            eventStore.getHandlers(eventClass).filterIsInstance<EventHandlerFactory<TEvent, *>>()

        val factoriesByHandlerClass = handlerFactories.associateBy { it.handlerType }

        return handlerClasses.map { handlerClass ->
            factoriesByHandlerClass[handlerClass]?.create()
                ?: error("No factory found for handler class: ${handlerClass.simpleName}")
        }
    }
}
