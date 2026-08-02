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
     * The commands this factory holds handlers for under [contextIdentity] — the producing module's
     * `kbus.boundedContextIdentity`, `""` for unassigned. One factory can serve several contexts,
     * so an identity it knows nothing about yields an empty set rather than everything.
     */
    fun commandTypesFor(contextIdentity: String): Set<KClass<out Command<*>>>

    /** The query equivalent of [commandTypesFor]. */
    fun queryTypesFor(contextIdentity: String): Set<KClass<out Query<*>>>
}
