package com.jimbroze.kbus.core.registry.generation

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import kotlin.reflect.KClass

// TODO delete this and use normal interface
interface GenerationHandlerFactory {
    fun <TCommand : Command<TResult>, TResult : KBusResult> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TResult>?

    fun <TQuery : Query<TResult>, TResult : KBusResult> handlerFor(
        query: TQuery
    ): QueryHandler<TQuery, TResult>?

    // Returns null if handler class not generated (not annotated with @LoadMessageHandler)
    fun eventHandlerFor(handlerClass: KClass<out EventHandler<*>>): EventHandler<*>?
}
