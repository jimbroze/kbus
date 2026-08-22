package com.jimbroze.kbus.core.registry.generation

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
import com.jimbroze.kbus.core.registry.DomainEventRegistrar
import com.jimbroze.kbus.core.registry.HandlerLocator
import com.jimbroze.kbus.core.registry.IntegrationEventRegistrar
import com.jimbroze.kbus.core.registry.persisting.PersistingEventHandlerStore
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import kotlin.reflect.KClass

// TODO type-safe generated event factory
class GenerationHandlerLocator(val generationHandlerFactory: GenerationHandlerFactory) :
    HandlerLocator {
    private val eventHandlerStore = PersistingEventHandlerStore()
    override val domainEventRegistrar = eventHandlerStore as DomainEventRegistrar
    override val integrationEventRegistrar = eventHandlerStore as IntegrationEventRegistrar

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

    override fun subscribedEventTypes(): Set<KClass<out Event>> =
        eventHandlerStore.subscribedEventTypes()

    override fun handledCommandTypes(): Set<KClass<out Command<*>>> = commandTypes

    override fun handledQueryTypes(): Set<KClass<out Query<*>>> = queryTypes

    override fun <TEvent : IntegrationEvent> handlersFor(
        event: TEvent,
        handlerDependencies: HandlerDependencies,
    ): List<EventHandler<TEvent>> {
        val handlerClasses = eventHandlerStore.handlerClassesFor(event)
        if (handlerClasses.isEmpty()) return emptyList()
        return handlerClasses.map<KClass<EventHandler<TEvent>>, EventHandler<TEvent>> { handlerClass
            ->
            generationHandlerFactory.eventHandler(handlerClass, handlerDependencies)
                ?: missingFactory(handlerClass)
        }
    }

    override fun <TEvent : DomainEvent> domainHandlersFor(
        event: TEvent,
        handlerDependencies: HandlerDependencies,
    ): List<DomainEventHandler<TEvent>> {
        val handlerClasses = eventHandlerStore.domainHandlerClassesFor(event)
        if (handlerClasses.isEmpty()) return emptyList()
        return handlerClasses.map<KClass<DomainEventHandler<TEvent>>, DomainEventHandler<TEvent>> {
            handlerClass ->
            generationHandlerFactory.domainEventHandler(handlerClass, handlerDependencies)
                ?: missingFactory(handlerClass)
        }
    }

    private fun missingFactory(handlerClass: KClass<*>): Nothing =
        error(
            "No generated factory for ${handlerClass.simpleName}. " +
                "Annotate it with @LoadMessageHandler."
        )
}
