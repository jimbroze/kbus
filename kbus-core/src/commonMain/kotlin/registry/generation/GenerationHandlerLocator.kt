package com.jimbroze.kbus.core.registry.generation

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.registry.DomainEventMapper
import com.jimbroze.kbus.core.registry.HandlerLocator
import com.jimbroze.kbus.core.registry.IntegrationEventMapper
import com.jimbroze.kbus.core.registry.persisting.PersistingEventMapper
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import kotlin.reflect.KClass

// TODO type-safe generated event factory
class GenerationHandlerLocator(val generationHandlerFactory: GenerationHandlerFactory) :
    HandlerLocator {
    private val eventMapper = PersistingEventMapper()
    override val domainEventMapper = eventMapper as DomainEventMapper
    override val integrationEventMapper = eventMapper as IntegrationEventMapper

    private val commandTypes = generationHandlerFactory.commandTypes()
    private val queryTypes = generationHandlerFactory.queryTypes()

    override fun <TCommand : Command<TResult>, TResult : KBusResult> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TResult>? {
        return generationHandlerFactory.handlerFor(command, commandDependencies)
    }

    override fun <TQuery : Query<TResult>, TResult : KBusResult> handlerFor(
        query: TQuery
    ): QueryHandler<TQuery, TResult>? {
        return generationHandlerFactory.handlerFor(query)
    }

    override fun subscribedEventTypes(): Set<KClass<out Event>> = eventMapper.subscribedEventTypes()

    override fun handledCommandTypes(): Set<KClass<out Command<*>>> = commandTypes

    override fun handledQueryTypes(): Set<KClass<out Query<*>>> = queryTypes

    override fun <TEvent : IntegrationEvent> handlersFor(
        event: TEvent
    ): List<EventHandler<TEvent>> {
        val handlerClasses = eventMapper.handlerClassesFor(event)
        if (handlerClasses.isEmpty()) return emptyList()
        return handlerClasses.map<KClass<EventHandler<TEvent>>, EventHandler<TEvent>> { handlerClass
            ->
            generationHandlerFactory.eventHandler(handlerClass) ?: missingFactory(handlerClass)
        }
    }

    override fun <TEvent : DomainEvent> domainHandlersFor(
        event: TEvent
    ): List<DomainEventHandler<TEvent>> {
        val handlerClasses = eventMapper.domainHandlerClassesFor(event)
        if (handlerClasses.isEmpty()) return emptyList()
        return handlerClasses.map<KClass<DomainEventHandler<TEvent>>, DomainEventHandler<TEvent>> {
            handlerClass ->
            generationHandlerFactory.domainEventHandler(handlerClass)
                ?: missingFactory(handlerClass)
        }
    }

    private fun missingFactory(handlerClass: KClass<*>): Nothing =
        error(
            "No generated factory for ${handlerClass.simpleName}. " +
                "Annotate it with @LoadMessageHandler."
        )
}
