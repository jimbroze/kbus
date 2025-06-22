package com.jimbroze.kbus.core

import kotlin.reflect.KClass

interface MessageHandlerMapper {
    fun <TCommand : Command> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, *, *>?

    fun <TQuery : Query> handlerFor(query: TQuery): QueryHandler<TQuery, *, *>?

    fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>>
}

interface HandlerFactory {
    fun <TCommand : Command, THandler : CommandHandler<TCommand, *, *>> create(
        handlerType: KClass<THandler>,
        commandDependencies: CommandDependencies,
    ): THandler

    fun <TQuery : Query, THandler : QueryHandler<TQuery, *, *>> create(
        handlerType: KClass<THandler>
    ): THandler
}

interface HandlerLocator {
    val messageMapper: MessageHandlerMapper
    val factory: HandlerFactory
}

interface HasEventManager : HandlerLocator {
    val eventManager: EventHandlerManager
}

interface EventHandlerManager {
    fun <TEvent : Event> register(
        eventType: KClass<TEvent>,
        handlerFactories: List<MessageHandlerFactory<TEvent, *>>,
    )

    fun <TEvent : Event> deregister(
        messageType: KClass<TEvent>,
        handlerTypes: List<KClass<out EventHandler<TEvent>>>,
    )

    fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>>
}
