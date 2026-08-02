package com.jimbroze.kbus.core.registry.persisting.store

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.contracts.common.MessageHandler
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.messages.HandlerDependencies
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import kotlin.reflect.KClass

sealed interface MessageHandlerFactory<TMessage : Message, THandler : MessageHandler<TMessage>> {
    val handlerType: KClass<THandler>
}

data class CommandHandlerFactory<
    TCommand : Command<TResult>,
    THandler : CommandHandler<TCommand, TResult>,
    TResult : KBusResult,
>(
    override val handlerType: KClass<THandler>,
    val create: (commandDependencies: CommandDependencies) -> THandler,
) : MessageHandlerFactory<TCommand, THandler>

data class QueryHandlerFactory<
    TQuery : Query<TResult>,
    THandler : QueryHandler<TQuery, TResult>,
    TResult : KBusResult,
>(override val handlerType: KClass<THandler>, val create: () -> THandler) :
    MessageHandlerFactory<TQuery, THandler>

data class EventHandlerFactory<TEvent : Event, THandler : EventHandler<TEvent>>(
    override val handlerType: KClass<THandler>,
    val create: (handlerDependencies: HandlerDependencies) -> THandler,
) : MessageHandlerFactory<TEvent, THandler>
