package com.jimbroze.kbus.core.registry.generation

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
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
     * The domain-handler equivalent of [eventHandler]. Only handlers generated for a [DomainEvent]
     * are reachable through it, so a domain lookup cannot return a handler that lacks the kind.
     */
    fun <TEvent : DomainEvent> domainEventHandler(
        handlerClass: KClass<DomainEventHandler<TEvent>>
    ): DomainEventHandler<TEvent>?

    /** The commands this factory holds handlers for — one bounded context's, and only its. */
    fun commandTypes(): Set<KClass<out Command<*>>>

    /** The query equivalent of [commandTypes]. */
    fun queryTypes(): Set<KClass<out Query<*>>>
}
