package com.jimbroze.kbus.core.registry.generation

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.registry.DomainEventMapper
import com.jimbroze.kbus.core.registry.HandlerLocator
import com.jimbroze.kbus.core.registry.IntegrationEventMapper
import com.jimbroze.kbus.core.registry.persisting.PersistingEventMapper
import kotlin.reflect.KClass

// TODO type-safe generated event factory
class GenerationHandlerLocator(
    val generationHandlerFactory: GenerationHandlerFactory,
    /**
     * The bounded context this locator answers for (`""` for the default). Needed because one
     * generated factory can hold handlers for several contexts.
     */
    private val contextIdentity: String = "",
) : HandlerLocator {
    private val eventMapper = PersistingEventMapper()
    override val domainEventMapper = eventMapper as DomainEventMapper
    override val integrationEventMapper = eventMapper as IntegrationEventMapper

    private val commandTypes = generationHandlerFactory.commandTypesFor(contextIdentity)
    private val queryTypes = generationHandlerFactory.queryTypesFor(contextIdentity)

    /**
     * A command another context owns is not resolvable here even though the shared factory can
     * build it — resolving it would let a nested execution cross a context boundary and silently
     * join the caller's transaction.
     */
    override fun <TCommand : Command<TResult>, TResult : KBusResult> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TResult>? {
        if (command::class !in commandTypes) return null
        return generationHandlerFactory.handlerFor(command, commandDependencies)
    }

    override fun <TQuery : Query<TResult>, TResult : KBusResult> handlerFor(
        query: TQuery
    ): QueryHandler<TQuery, TResult>? {
        if (query::class !in queryTypes) return null
        return generationHandlerFactory.handlerFor(query)
    }

    override fun subscribedEventTypes(): Set<KClass<out Event>> = eventMapper.subscribedEventTypes()

    override fun handledCommandTypes(): Set<KClass<out Command<*>>> = commandTypes

    override fun handledQueryTypes(): Set<KClass<out Query<*>>> = queryTypes

    override fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>> {
        val handlerClasses = eventMapper.handlerClassesFor(event)
        if (handlerClasses.isEmpty()) return emptyList()
        return handlerClasses.map<KClass<EventHandler<TEvent>>, EventHandler<TEvent>> { handlerClass
            ->
            generationHandlerFactory.eventHandler(handlerClass)
                ?: error(
                    "No generated factory for ${handlerClass.simpleName}. " +
                        "Annotate it with @LoadMessageHandler."
                )
        }
    }
}
