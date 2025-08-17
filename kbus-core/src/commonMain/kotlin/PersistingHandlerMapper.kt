package com.jimbroze.kbus.core

import com.jimbroze.kbus.core.domain.DomainEvent
import kotlin.collections.forEach
import kotlin.reflect.KClass

// class PersistingHandlerFactory(private val stores: HandlerFactoryStoreCollection) :
// HandlerFactory {
//    override fun <
//        TCommand : Command<TResult>,
//        THandler : CommandHandler<TCommand, TResult>,
//        TResult : KBusResult,
//    > create(handlerType: KClass<THandler>, commandDependencies: CommandDependencies): THandler {
//        val factories = stores.commandStore.getHandlersByType(handlerType)
//
//        if (factories.size > 1) {
//            throw TooManyHandlersException()
//        }
//
//        val factory =
//            factories.firstOrNull()
//                ?: throw IllegalArgumentException(
//                    "No handler found for command type ${handlerType.simpleName}."
//                )
//
//        require(factory is CommandHandlerFactory<TCommand, THandler, *>) {
//            "Factory for command handler ${handlerType.simpleName} was incorrectly registered"
//        }
//
//        return factory.create(commandDependencies)
//    }
//
//    override fun <
//        TQuery : Query<TResult>,
//        THandler : QueryHandler<TQuery, TResult>,
//        TResult : KBusResult,
//    > create(handlerType: KClass<THandler>): THandler {
//        val handlers = stores.queryStore.getHandlersByType(handlerType)
//
//        if (handlers.size > 1) {
//            throw TooManyHandlersException()
//        }
//
//        val factory =
//            handlers.firstOrNull()
//                ?: throw IllegalArgumentException(
//                    "No handler found for query type ${handlerType.simpleName}."
//                )
//
//        require(factory is QueryHandlerFactory<TQuery, THandler, *>) {
//            "Factory for query handler ${handlerType.simpleName} was incorrectly registered"
//        }
//
//        return factory.create()
//    }
//
//    override fun <TEvent : Event, THandler : EventHandler<TEvent>> create(
//        handlerType: KClass<THandler>
//    ): THandler {
//        val handlers = stores.eventStore.getHandlersByType(handlerType)
//
//        if (handlers.size > 1) {
//            throw TooManyHandlersException()
//        }
//
//        val factory =
//            handlers.firstOrNull()
//                ?: throw IllegalArgumentException(
//                    "No handler found for event type ${handlerType.simpleName}."
//                )
//
//        require(factory is EventHandlerFactory<TEvent, THandler>) {
//            "Factory for event handler ${handlerType.simpleName} was incorrectly registered"
//        }
//
//        return factory.create()
//    }
// }

data class HandlerFactoryStoreCollection(
    val commandStore: MessageHandlerFactoryStore<Command<*>> = MessageHandlerFactoryStore(),
    val queryStore: MessageHandlerFactoryStore<Query<*>> = MessageHandlerFactoryStore(),
    val eventStore: MessageHandlerFactoryStore<Event> = MessageHandlerFactoryStore(),
)

class PersistingHandlerRegistrar(
    stores: HandlerFactoryStoreCollection = HandlerFactoryStoreCollection()
) {
    private val commandStore: MessageHandlerFactoryStore<Command<*>> = stores.commandStore
    private val queryStore: MessageHandlerFactoryStore<Query<*>> = stores.queryStore
    private val eventStore: MessageHandlerFactoryStore<Event> = stores.eventStore

    fun <TCommand : Command<*>> register(
        commandType: KClass<TCommand>,
        handlerFactory: CommandHandlerFactory<TCommand, *, *>,
    ) {
        check(!this.commandStore.isRegistered(commandType)) {
            "A Command Handler for command type ${commandType.simpleName} is already registered."
        }

        this.commandStore.registerHandlers(commandType, listOf(handlerFactory))
    }

    fun <TQuery : Query<*>> register(
        queryType: KClass<TQuery>,
        handlerFactory: QueryHandlerFactory<TQuery, *, *>,
    ) {
        check(!this.queryStore.isRegistered(queryType)) {
            "A Query Handler for query type ${queryType.simpleName} is already registered."
        }

        this.queryStore.registerHandlers(queryType, listOf(handlerFactory))
    }

    fun <TEvent : Event> register(
        eventType: KClass<TEvent>,
        handlerFactories: List<EventHandlerFactory<TEvent, *>>,
    ) {
        this.eventStore.registerHandlers(eventType, handlerFactories)
    }
}

data class EventAndHandlerFactories<TEvent : Event>(
    val event: KClass<TEvent>,
    val factories: List<EventHandlerFactory<TEvent, *>>,
)

data class EventAndHandlerClasses<TEvent : Event>(
    val event: KClass<TEvent>,
    val handlers: List<KClass<EventHandler<TEvent>>>,
)

// FIXME are we happy with this?
val mappings =
    listOf(
        EventAndHandlerClasses(
            DomainEvent::class,
            listOf(DomainEventHandler::class, DomainEventHandler::class),
        ),
        EventAndHandlerClasses(
            IntegrationEvent::class,
            listOf(IntegrationEventHandler::class, IntegrationEventHandler::class),
        ),
    )

// Application layer
interface DomainEventMapper {
    fun addDomainHandlers(mappings: List<EventAndHandlerClasses<out DomainEvent>>)
}

// Top layer
interface IntegrationEventMapper {
    fun addEventHandlers(mappings: List<EventAndHandlerClasses<out IntegrationEvent>>)
}

interface InlineIntegrationEventMapper {
    fun addEventHandlers(mappings: List<EventAndHandlerFactories<out IntegrationEvent>>)

    fun removeEventHandlers(mappings: List<EventAndHandlerFactories<out IntegrationEvent>>)
}

interface EventMapperProvider {
    val domainEventMapper: DomainEventMapper
    val integrationEventMapper: IntegrationEventMapper
    val inlineIntegrationEventMapper: InlineIntegrationEventMapper
}

// TODO add methods or props to get class as interfaces
// FIXME incorporate store??? How to work with generated?
// HandlersFor uses both stored and generated handlers. In order of mappings (stored first).
// Save
internal class EventMapper(private val eventFactory: EventFactory) :
    DomainEventMapper, IntegrationEventMapper, InlineIntegrationEventMapper {
    private val mappings = mutableMapOf<KClass<out Event>, List<KClass<EventHandler<*>>>>()
    private val inlineMappings = mutableMapOf<KClass<out Event>, List<EventHandlerFactory<*, *>>>()

    override fun addDomainHandlers(mappings: List<EventAndHandlerClasses<out DomainEvent>>) {
        mappings.forEach { mapping -> this.mappings[mapping.event] = mapping.handlers }
    }

    override fun addEventHandlers(mappings: List<EventAndHandlerClasses<out IntegrationEvent>>) {
        mappings.forEach { mapping -> this.mappings[mapping.event] = mapping.handlers }
    }

    override fun addEventHandlers(mappings: List<EventAndHandlerFactories<out IntegrationEvent>>) {
        mappings.forEach { mapping -> this.inlineMappings[mapping.event] = mapping.factories }
    }

    override fun removeEventHandlers(
        mappings: List<EventAndHandlerFactories<out IntegrationEvent>>
    ) {
        mappings.forEach { mappingToRemove ->
            val eventType = mappingToRemove.event
            val currentHandlers = this.mappings[eventType] ?: return@forEach

            val updatedHandlers = currentHandlers - mappingToRemove.factories.toSet()

            if (updatedHandlers.isEmpty()) {
                this.mappings.remove(eventType)
            } else {
                this.mappings[eventType] = updatedHandlers
            }
        }
    }

    fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>> {
        val inlineFactoryHandlers =
            inlineMappings[event::class]?.map { factory ->
                require(factory is EventHandlerFactory<TEvent, *>) {
                    "Inline event factory was incorrectly registered for event type ${event::class.simpleName}"
                }
                factory.create()
            } ?: emptyList()

        val otherHandlerClasses = mappings[event::class] as List<KClass<EventHandler<TEvent>>>
        val otherHandlers = eventFactory.create(event::class, otherHandlerClasses)

        return inlineFactoryHandlers + otherHandlers
    }
}

interface EventFactory {
    fun <TEvent : Event> create(
        eventClass: KClass<TEvent>,
        handlerClasses: List<KClass<EventHandler<TEvent>>>,
    ): List<EventHandler<TEvent>>
}

// FIXME either make eventmapper a public property of the locator or pass it in. Could pass in
// function to it?
class PersistingHandlerLocator(
    stores: HandlerFactoryStoreCollection = HandlerFactoryStoreCollection()
) : MessageHandlerLocator, EventMapperProvider {
    private val commandStore: MessageHandlerFactoryStore<Command<*>> = stores.commandStore
    private val queryStore: MessageHandlerFactoryStore<Query<*>> = stores.queryStore
    private val eventMapper = EventMapper(PersistingEventFactory(stores.eventStore))

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

    // FIXME factories??
    override fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>> {
        return eventMapper.handlersFor(event)
    }

    internal class PersistingEventFactory(val eventStore: MessageHandlerFactoryStore<Event>) :
        EventFactory {
        override fun <TEvent : Event> create(
            eventClass: KClass<TEvent>,
            handlerClasses: List<KClass<EventHandler<TEvent>>>,
        ): List<EventHandler<TEvent>> {
            val handlerFactories =
                eventStore.getHandlers(eventClass).filter { factory ->
                    handlerClasses.any { handlerClass -> factory::class == handlerClass }
                }

            return handlerClasses.map { handlerClass ->
                handlerFactories
                    .firstOrNull { factory -> factory::class == handlerClass }
                    .let { factory ->
                        require(factory is EventHandlerFactory<TEvent, *>) {
                            "Event factory was incorrectly registered for query type ${eventClass::class.simpleName}"
                        }

                        factory.create()
                    }
            }
        }
    }
}
