package com.jimbroze.kbus.core.registry

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.messages.HandlerDependencies
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import kotlin.reflect.KClass

interface HandlerLocator : EventMapperProvider {
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

    /**
     * The domain equivalent of [handlersFor]. Separate because domain dispatch needs the handler
     * kind, not merely something that can handle the event.
     */
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
