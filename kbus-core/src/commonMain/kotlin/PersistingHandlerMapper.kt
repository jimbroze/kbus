package com.jimbroze.kbus.core

import kotlin.jvm.JvmName
import kotlin.reflect.KClass

class PersistingHandlerLocator(
    stores: HandlerFactoryStoreCollection = HandlerFactoryStoreCollection()
) : HandlerLocator, HasEventManager {
    private val persistingMapper = PersistingHandlerMapper(stores)
    override val messageMapper: MessageHandlerMapper = persistingMapper
    override val eventManager: EventHandlerManager = persistingMapper

    override val factory: HandlerFactory = PersistingHandlerFactory(stores)
}

class PersistingHandlerFactory(private val stores: HandlerFactoryStoreCollection) : HandlerFactory {
    override fun <TCommand : Command, THandler : CommandHandler<TCommand, *, *>> create(
        handlerType: KClass<THandler>,
        commandDependencies: CommandDependencies,
    ): THandler {
        val factories = stores.commandStore.getHandlersByType(handlerType)

        if (factories.size > 1) {
            throw TooManyHandlersException()
        }

        val factory =
            factories.firstOrNull()
                ?: throw IllegalArgumentException(
                    "No handler found for command type ${handlerType.simpleName}."
                )

        require(factory is CommandHandlerFactory<TCommand, THandler>) {
            "Factory for command handler ${handlerType::class.simpleName} was incorrectly registered"
        }

        return factory.create(commandDependencies)
    }

    override fun <TQuery : Query, THandler : QueryHandler<TQuery, *, *>> create(
        handlerType: KClass<THandler>
    ): THandler {
        val handlers = stores.queryStore.getHandlersByType(handlerType)

        if (handlers.size > 1) {
            throw TooManyHandlersException()
        }

        val factory =
            handlers.firstOrNull()
                ?: throw IllegalArgumentException(
                    "No handler found for query type ${handlerType.simpleName}."
                )

        require(factory is QueryHandlerFactory<TQuery, THandler>) {
            "Factory for query handler ${handlerType::class.simpleName} was incorrectly registered"
        }

        return factory.create()
    }
}

data class HandlerFactoryStoreCollection(
    val commandStore: MessageHandlerFactoryStore<Command> = MessageHandlerFactoryStore(),
    val queryStore: MessageHandlerFactoryStore<Query> = MessageHandlerFactoryStore(),
    val eventStore: MessageHandlerFactoryStore<Event> = MessageHandlerFactoryStore(),
)

class PersistingHandlerMapper(
    stores: HandlerFactoryStoreCollection = HandlerFactoryStoreCollection()
) : MessageHandlerMapper, EventHandlerManager {
    private val commandStore: MessageHandlerFactoryStore<Command> = stores.commandStore
    private val queryStore: MessageHandlerFactoryStore<Query> = stores.queryStore
    private val eventStore: MessageHandlerFactoryStore<Event> = stores.eventStore

    override fun <TCommand : Command> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, *, *>? {
        val factory = commandStore.getHandlers(command::class).firstOrNull() ?: return null

        require(factory is CommandHandlerFactory<TCommand, *>) {
            "Command factory was incorrectly registered for command type ${command::class.simpleName}"
        }

        return factory.create(commandDependencies)
    }

    override fun <TQuery : Query> handlerFor(query: TQuery): QueryHandler<TQuery, *, *>? {
        val factory = queryStore.getHandlers(query::class).firstOrNull() ?: return null

        require(factory is QueryHandlerFactory<TQuery, *>) {
            "Query factory was incorrectly registered for query type ${query::class.simpleName}"
        }

        return factory.create() as QueryHandler<TQuery, *, *>?
    }

    override fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>> {
        return eventStore.getHandlers(event::class).map {
            require(it is EventHandlerFactory<TEvent, *>) {
                "Event factory was incorrectly registered for query type ${event::class.simpleName}"
            }
            it.create()
        }
    }

    @JvmName("registerCommand")
    fun <TCommand : Command> register(
        commandType: KClass<TCommand>,
        handlerFactory: MessageHandlerFactory<TCommand, *>,
    ) {
        // FiXME do we need to check this? Yes for deserialization
        check(!this.commandStore.isRegistered(commandType)) {
            "A Command Handler for command type ${commandType.simpleName} is already registered."
        }

        this.commandStore.registerHandlers(commandType, listOf(handlerFactory))
    }

    @JvmName("deregisterCommand")
    fun <TCommand : Command> deregister(
        messageType: KClass<TCommand>,
        handlerType: KClass<out CommandHandler<TCommand, *, *>>,
    ) {
        this.commandStore.removeHandlers(messageType, listOf(handlerType))
    }

    @JvmName("registerQuery")
    fun <TQuery : Query> register(
        queryType: KClass<TQuery>,
        handlerFactory: MessageHandlerFactory<TQuery, *>,
    ) {
        check(!this.queryStore.isRegistered(queryType)) {
            "A Query Handler for query type ${queryType.simpleName} is already registered."
        }

        this.queryStore.registerHandlers(queryType, listOf(handlerFactory))
    }

    @JvmName("deregisterQuery")
    fun <TQuery : Query> deregister(
        messageType: KClass<TQuery>,
        handlerType: KClass<out QueryHandler<TQuery, *, *>>,
    ) {
        this.queryStore.removeHandlers(messageType, listOf(handlerType))
    }

    override fun <TEvent : Event> register(
        eventType: KClass<TEvent>,
        handlerFactories: List<MessageHandlerFactory<TEvent, *>>,
    ) {
        this.eventStore.registerHandlers(eventType, handlerFactories)
    }

    override fun <TEvent : Event> deregister(
        messageType: KClass<TEvent>,
        handlerTypes: List<KClass<out EventHandler<TEvent>>>,
    ) {
        this.eventStore.removeHandlers(messageType, handlerTypes)
    }
}
