package com.jimbroze.kbus.core.registry

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.api.messages.event.Event
import com.jimbroze.kbus.api.messages.event.EventHandler
import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import com.jimbroze.kbus.api.messages.query.Query
import com.jimbroze.kbus.api.messages.query.QueryHandler
import com.jimbroze.kbus.api.result.KBusResult
import com.jimbroze.kbus.application.messages.HandlerDependencies
import com.jimbroze.kbus.application.messages.command.CommandDependencies
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import kotlin.reflect.KClass

interface HandlerLocator : EventRegistrarProvider {
    fun <TCommand : Command<TResult>, TResult : KBusResult> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TResult>?

    fun <TQuery : Query<TResult>, TResult : KBusResult> handlerFor(
        query: TQuery
    ): QueryHandler<TQuery, TResult>?

    fun <TEvent : IntegrationEvent> handlersFor(
        event: TEvent,
        handlerDependencies: HandlerDependencies,
    ): List<EventHandler<TEvent>>

    fun <TEvent : DomainEvent> domainHandlersFor(
        event: TEvent,
        handlerDependencies: HandlerDependencies,
    ): List<DomainEventHandler<TEvent>>

    /**
     * Every event class this locator has a handler registered for, **without instantiating any of
     * them** — [handlersFor] creates every handler it finds, so it must not back this.
     */
    fun subscribedEventTypes(): Set<KClass<out Event>>

    /**
     * Every command class this locator has a handler registered for, **without instantiating any of
     * them** — [handlerFor] creates the handler it finds, so it must not back this.
     */
    fun handledCommandTypes(): Set<KClass<out Command<*>>>

    /**
     * The query equivalent of [handledCommandTypes], under the same no-instantiation requirement.
     */
    fun handledQueryTypes(): Set<KClass<out Query<*>>>
}
