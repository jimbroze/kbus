package com.jimbroze.kbus.core.registry.persisting

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.messages.HandlerDependencies
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.registry.DomainEventMapper
import com.jimbroze.kbus.core.registry.HandlerLocator
import com.jimbroze.kbus.core.registry.IntegrationEventMapper
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.persisting.store.MessageHandlerFactoryStore
import com.jimbroze.kbus.core.registry.persisting.store.QueryHandlerFactory
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
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

    override fun subscribedEventTypes(): Set<KClass<out Event>> = eventMapper.subscribedEventTypes()

    override fun handledCommandTypes(): Set<KClass<out Command<*>>> = commandStore.registeredTypes()

    override fun handledQueryTypes(): Set<KClass<out Query<*>>> = queryStore.registeredTypes()

    override fun <TEvent : IntegrationEvent> handlersFor(
        event: TEvent,
        handlerDependencies: HandlerDependencies,
    ): List<EventHandler<TEvent>> =
        createHandlers(event, eventMapper.handlerClassesFor(event), handlerDependencies)

    /**
     * The cast holds because a factory is looked up by the handler class that registered it, and
     * the domain mapper accepts none but [DomainEventHandler] classes.
     */
    override fun <TEvent : DomainEvent> domainHandlersFor(
        event: TEvent,
        handlerDependencies: HandlerDependencies,
    ): List<DomainEventHandler<TEvent>> {
        val handlers =
            createHandlers(event, eventMapper.domainHandlerClassesFor(event), handlerDependencies)
        @Suppress("UNCHECKED_CAST")
        return handlers as List<DomainEventHandler<TEvent>>
    }

    private fun <TEvent : Event> createHandlers(
        event: TEvent,
        handlerClasses: List<KClass<out EventHandler<TEvent>>>,
        handlerDependencies: HandlerDependencies,
    ): List<EventHandler<TEvent>> {
        if (handlerClasses.isEmpty()) return emptyList()
        @Suppress("UNCHECKED_CAST") val eventClass = event::class as KClass<TEvent>

        val handlerFactories =
            eventStore.getHandlers(eventClass).filterIsInstance<EventHandlerFactory<TEvent, *>>()

        val factoriesByHandlerClass = handlerFactories.associateBy { it.handlerType }

        return handlerClasses.map { handlerClass ->
            factoriesByHandlerClass[handlerClass]?.create(handlerDependencies)
                ?: error("No factory found for handler class: ${handlerClass.simpleName}")
        }
    }
}
