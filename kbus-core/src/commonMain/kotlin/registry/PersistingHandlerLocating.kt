package com.jimbroze.kbus.core.registry

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import kotlin.reflect.KClass

data class HandlerFactoryStoreCollection(
    val commandStore: MessageHandlerFactoryStore<Command<*>> = MessageHandlerFactoryStore(),
    val queryStore: MessageHandlerFactoryStore<Query<*>> = MessageHandlerFactoryStore(),
    val eventStore: MessageHandlerFactoryStore<Event> = MessageHandlerFactoryStore(),
)

data class EventAndHandlerFactories<TEvent : Event>(
    val event: KClass<TEvent>,
    val factories: List<EventHandlerFactory<TEvent, *>>,
)

data class EventHandlerMapping<TEvent : Event>(
    val event: KClass<TEvent>,
    val handlers: List<KClass<out EventHandler<TEvent>>>,
)

class PersistingEventFactory(val eventStore: MessageHandlerFactoryStore<Event>) : EventFactory {
    override fun <TEvent : Event> create(
        eventClass: KClass<TEvent>,
        handlerClasses: List<KClass<EventHandler<TEvent>>>,
    ): List<EventHandler<TEvent>> {
        val handlerFactories =
            eventStore.getHandlers(eventClass).filterIsInstance<EventHandlerFactory<TEvent, *>>()

        val factoriesByHandlerClass = handlerFactories.associateBy { it.handlerType }

        return handlerClasses.map { handlerClass ->
            factoriesByHandlerClass[handlerClass]?.create()
                ?: error("No factory found for handler class: ${handlerClass.simpleName}")
        }
    }
}

class PersistingHandlerLocator(
    stores: HandlerFactoryStoreCollection = HandlerFactoryStoreCollection()
) : HandlerLocator, EventMapperProvider {
    private val commandStore: MessageHandlerFactoryStore<Command<*>> = stores.commandStore
    private val queryStore: MessageHandlerFactoryStore<Query<*>> = stores.queryStore
    private val eventMapper = DefaultEventMapper(PersistingEventFactory(stores.eventStore))
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
