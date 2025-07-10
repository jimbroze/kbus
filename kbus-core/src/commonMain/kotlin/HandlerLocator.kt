package com.jimbroze.kbus.core

import kotlin.reflect.KClass

interface MessageHandlerMapper {
    fun <TCommand : Command<TResult>, TResult : KBusResult> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TResult>?

    fun <TQuery : Query<TResult>, TResult : KBusResult> handlerFor(
        query: TQuery
    ): QueryHandler<TQuery, TResult>?

    fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>>
}

interface HandlerFactory {
    fun <
        TCommand : Command<TResult>,
        THandler : CommandHandler<TCommand, TResult>,
        TResult : KBusResult,
    > create(handlerType: KClass<THandler>, commandDependencies: CommandDependencies): THandler

    fun <
        TQuery : Query<TResult>,
        THandler : QueryHandler<TQuery, TResult>,
        TResult : KBusResult,
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
