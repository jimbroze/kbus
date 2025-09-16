package com.jimbroze.kbus.core

import com.jimbroze.kbus.core.domain.DomainEvent
import kotlin.collections.forEach
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

class EventMapper(private val eventFactory: EventFactory) :
    DomainEventMapper, IntegrationEventMapper, InlineIntegrationEventMapper {
    private val mappings = mutableMapOf<KClass<out Event>, List<KClass<EventHandler<*>>>>()
    private val inlineMappings = mutableMapOf<KClass<out Event>, List<EventHandlerFactory<*, *>>>()

    override fun addDomainHandlers(mappings: List<EventHandlerMapping<out DomainEvent>>) {
        @Suppress("UNCHECKED_CAST")
        mappings.forEach { mapping ->
            this.mappings[mapping.event] = mapping.handlers as List<KClass<EventHandler<*>>>
        }
    }

    override fun addEventHandlers(mappings: List<EventHandlerMapping<out IntegrationEvent>>) {
        @Suppress("UNCHECKED_CAST")
        mappings.forEach { mapping ->
            this.mappings[mapping.event] = mapping.handlers as List<KClass<EventHandler<*>>>
        }
    }

    override fun addInlineEventHandlers(
        mappings: List<EventAndHandlerFactories<out IntegrationEvent>>
    ) {
        mappings.forEach { mapping -> this.inlineMappings[mapping.event] = mapping.factories }
    }

    override fun removeInlineEventHandlers(
        mappings: List<EventAndHandlerFactories<out IntegrationEvent>>
    ) {
        mappings.forEach { mappingToRemove ->
            val eventType = mappingToRemove.event
            val currentHandlers = this.inlineMappings[eventType] ?: return@forEach

            val updatedHandlers = currentHandlers - mappingToRemove.factories.toSet()

            if (updatedHandlers.isEmpty()) {
                this.inlineMappings.remove(eventType)
            } else {
                this.inlineMappings[eventType] = updatedHandlers
            }
        }
    }

    fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>> {
        val inlineFactoryHandlers =
            (inlineMappings[event::class]?.mapNotNull { factory ->
                @Suppress("UNCHECKED_CAST") (factory as? EventHandlerFactory<TEvent, *>)?.create()
            } ?: emptyList())

        @Suppress("UNCHECKED_CAST") val eventClass = event::class as? KClass<TEvent>
        @Suppress("UNCHECKED_CAST")
        val otherHandlerClasses = mappings[event::class] as? List<KClass<EventHandler<TEvent>>>
        val otherHandlers =
            if (otherHandlerClasses != null && eventClass != null) {
                eventFactory.create(eventClass, otherHandlerClasses)
            } else {
                emptyList()
            }

        return inlineFactoryHandlers + otherHandlers
    }
}

interface EventFactory {
    fun <TEvent : Event> create(
        eventClass: KClass<TEvent>,
        handlerClasses: List<KClass<EventHandler<TEvent>>>,
    ): List<EventHandler<TEvent>>
}

// TODO should we allow multiple of same handler?
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
) : MessageHandlerLocator, EventMapperProvider {
    private val commandStore: MessageHandlerFactoryStore<Command<*>> = stores.commandStore
    private val queryStore: MessageHandlerFactoryStore<Query<*>> = stores.queryStore
    private val eventMapper = EventMapper(PersistingEventFactory(stores.eventStore))
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
