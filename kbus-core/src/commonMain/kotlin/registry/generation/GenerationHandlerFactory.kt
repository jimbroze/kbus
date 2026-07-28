package com.jimbroze.kbus.core.registry.generation

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import kotlin.reflect.KClass

interface GenerationHandlerFactory {
    fun <TCommand : Command<TResult>, TResult : KBusResult> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TResult>?

    fun <TQuery : Query<TResult>, TResult : KBusResult> handlerFor(
        query: TQuery
    ): QueryHandler<TQuery, TResult>?

    fun <TEvent : Event> eventHandler(
        handlerClass: KClass<EventHandler<TEvent>>
    ): EventHandler<TEvent>?

    /**
     * The bounded context identity (the producing module's `kbus.boundedContextIdentity`, `""` for
     * unassigned) this factory's handler for [commandClass] was generated with, or `null` if this
     * factory has no handler for it at all.
     */
    fun commandModule(commandClass: KClass<out Command<*>>): String?

    /** The query equivalent of [commandModule]. */
    fun queryModule(queryClass: KClass<out Query<*>>): String?
}
