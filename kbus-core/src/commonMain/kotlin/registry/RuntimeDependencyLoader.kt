package com.jimbroze.kbus.core.registry

import kotlin.reflect.KClass

abstract class RuntimeDependencyLoader {
    abstract fun <TClass : Any> instantiate(cls: KClass<TClass>): TClass

    fun <TClass : Any> load(cls: KClass<TClass>): TClass {
        return instantiate(cls)
    }
}

// TODO need a way of registering mappings only. HandlerManager?
// class LoaderHandlerLocator(
//    stores: HandlerFactoryStoreCollection = HandlerFactoryStoreCollection()
// ) : HandlerLocator, HasEventManager {
//    private val persistingMapper = PersistingHandlerMapper(stores)
//    override val messageMapper: MessageHandlerMapper = persistingMapper
//    override val eventManager: EventHandlerManager = persistingMapper
//
//    override val factory: HandlerFactory = LoaderHandlerFactory(loader)
// }
//
// class LoaderHandlerFactory(private val loader: RuntimeDependencyLoader) : HandlerFactory {
//    override fun <TCommand : Command, THandler : CommandHandler<TCommand, *, *>> create(
//        handlerType: KClass<THandler>
//    ): THandler {
//        return loader.load(handlerType)
//    }
//
//    override fun <TQuery : Query, THandler : QueryHandler<TQuery, *, *>> create(
//        handlerType: KClass<THandler>
//    ): THandler {
//        return loader.load(handlerType)
//    }
// }
//
// class LoaderHandlerMapper(
//    store: PersistingHandlerMapper
// ) : MessageHandlerMapper {
//
//    override fun <TCommand : Command> handlerFor(
//        command: TCommand
//    ): CommandHandler<TCommand, *, *>? {
//        return sto
//    }
//
//    override fun <TQuery : Query> handlerFor(query: TQuery): QueryHandler<TQuery, *, *>? {
//    }
//
//    override fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>> {
//    }
//
//    @JvmName("registerCommand")
//    fun <TCommand : Command> register(
//        commandType: KClass<TCommand>,
//        handlerFactory: MessageHandlerFactory<TCommand, *>,
//    ) {
//    }
//
//    @JvmName("deregisterCommand")
//    fun <TCommand : Command> deregister(
//    }
//
//    @JvmName("registerQuery")
//    fun <TQuery : Query> register(
//        queryType: KClass<TQuery>,
//        handlerFactory: MessageHandlerFactory<TQuery, *>,
//    ) {
//    }
//
//    @JvmName("deregisterQuery")
//    fun <TQuery : Query> deregister(
//        messageType: KClass<TQuery>,
//        handlerType: KClass<out QueryHandler<TQuery, *, *>>,
//    ) {
//    }
//
//    override fun <TEvent : Event> register(
//        eventType: KClass<TEvent>,
//        handlerFactories: List<MessageHandlerFactory<TEvent, *>>,
//    ) {
//    }
//
//    override fun <TEvent : Event> deregister(
//        messageType: KClass<TEvent>,
//        handlerTypes: List<KClass<out EventHandler<TEvent>>>,
//    ) {
//    }
// }
