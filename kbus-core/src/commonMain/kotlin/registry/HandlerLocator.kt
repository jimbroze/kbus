package com.jimbroze.kbus.core.registry

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import kotlin.reflect.KClass

interface HandlerLocator : EventMapperProvider {
    fun <TCommand : Command<TResult>, TResult : KBusResult> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TResult>?

    fun <TQuery : Query<TResult>, TResult : KBusResult> handlerFor(
        query: TQuery
    ): QueryHandler<TQuery, TResult>?

    fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>>

    /**
     * Every event class this locator has a handler registered for, **without instantiating any of
     * them** — `handlersFor` creates every handler it finds, so it must not back this. Read once
     * per context when the bus is built, to derive that context's subscriptions.
     */
    fun subscribedEventTypes(): Set<KClass<out Event>>

    /**
     * Whether this locator owns a handler for [command], **without instantiating it** — used to
     * find a command's owning bounded context, so this runs on every `execute` call. Do not
     * implement this as `handlerFor(command, ...) != null`.
     */
    fun hasHandlerFor(command: Command<*>): Boolean

    /**
     * Whether this locator owns a handler for [query], **without instantiating it** — used to find
     * a query's owning bounded context, so this runs on every `fetch` call. Do not implement this
     * as `handlerFor(query) != null`.
     */
    fun hasHandlerFor(query: Query<*>): Boolean
}
