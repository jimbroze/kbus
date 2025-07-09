package com.jimbroze.kbus.core

import kotlin.reflect.KClass

interface MessageHandlerMapper {
    fun <TCommand : Command<TReturn, TFailure>, TReturn, TFailure : MessageFailure> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TReturn, TFailure>?

    fun <TQuery : Query<TReturn, TFailure>, TReturn, TFailure : MessageFailure> handlerFor(
        query: TQuery
    ): QueryHandler<TQuery, TReturn, TFailure>?

    fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>>
}

interface HandlerFactory {
    fun <
        TCommand : Command<TReturn, TFailure>,
        THandler : CommandHandler<TCommand, TReturn, TFailure>,
        TReturn,
        TFailure : MessageFailure,
    > create(handlerType: KClass<THandler>, commandDependencies: CommandDependencies): THandler

    fun <
        TQuery : Query<TReturn, TFailure>,
        THandler : QueryHandler<TQuery, TReturn, TFailure>,
        TReturn,
        TFailure : MessageFailure,
    > create(handlerType: KClass<THandler>): THandler
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
        handlerFactories: List<EventHandlerFactory<TEvent, *>>,
    )

    fun <TEvent : Event> deregister(
        messageType: KClass<TEvent>,
        handlerTypes: List<KClass<out EventHandler<TEvent>>>,
    )

    fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>>
}
