package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.core.Command
import com.jimbroze.kbus.core.CommandDependencies
import com.jimbroze.kbus.core.CommandHandler
import com.jimbroze.kbus.core.DomainEventMapper
import com.jimbroze.kbus.core.Event
import com.jimbroze.kbus.core.EventHandler
import com.jimbroze.kbus.core.EventMapper
import com.jimbroze.kbus.core.EventMapperProvider
import com.jimbroze.kbus.core.InlineIntegrationEventMapper
import com.jimbroze.kbus.core.IntegrationEventMapper
import com.jimbroze.kbus.core.KBusResult
import com.jimbroze.kbus.core.MessageHandlerFactoryStore
import com.jimbroze.kbus.core.MessageHandlerLocator
import com.jimbroze.kbus.core.PersistingEventFactory
import com.jimbroze.kbus.core.Query
import com.jimbroze.kbus.core.QueryHandler

class GeneratedHandlerLocator(dependencies: IContainer) :
    MessageHandlerLocator, EventMapperProvider {
    internal val generatedHandlerFactory = GeneratedHandlerFactory(dependencies)
    private val eventMapper = EventMapper(PersistingEventFactory(MessageHandlerFactoryStore()))
    override val domainEventMapper = eventMapper as DomainEventMapper
    override val integrationEventMapper = eventMapper as IntegrationEventMapper
    override val inlineIntegrationEventMapper = eventMapper as InlineIntegrationEventMapper

    override fun <TCommand : Command<TResult>, TResult : KBusResult> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TResult>? {
        return generatedHandlerFactory.handlerFor(command, commandDependencies)
    }

    override fun <TQuery : Query<TResult>, TResult : KBusResult> handlerFor(
        query: TQuery
    ): QueryHandler<TQuery, TResult>? {
        return generatedHandlerFactory.handlerFor(query)
    }

    override fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>> {
        return eventMapper.handlersFor(event)
    }
}

class GeneratedHandlerFactory(private val dependencies: IContainer) : IHandlers {
    fun <TCommand : Command<TResult>, TResult : KBusResult> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TResult>? {
        @Suppress("UNCHECKED_CAST")
        return when (command) {
            is TestGeneratorCommand -> this.testGeneratorCommandHandler(commandDependencies)
            is TestDuplicateGeneratorCommand ->
                this.testDuplicateGeneratorCommandHandler(commandDependencies)
            else -> null
        }
            as CommandHandler<TCommand, TResult>?
    }

    fun <TQuery : Query<TResult>, TResult : KBusResult> handlerFor(
        query: TQuery
    ): QueryHandler<TQuery, TResult>? {
        @Suppress("UNCHECKED_CAST")
        return when (query) {
            is TestGeneratorQuery -> this.testGeneratorQueryHandler()
            else -> null
        }
            as QueryHandler<TQuery, TResult>?
    }

    override fun testGeneratorCommandHandler(
        commandDependencies: CommandDependencies
    ): TestGeneratorCommandHandler {
        return TestGeneratorCommandHandler(
            this.dependencies.busLocker,
            this.dependencies.containsInstant(commandDependencies),
            this.dependencies.containsString,
        )
    }

    override fun testDuplicateGeneratorCommandHandler(
        commandDependencies: CommandDependencies
    ): TestDuplicateGeneratorCommandHandler {
        return TestDuplicateGeneratorCommandHandler(
            this.dependencies.clockFactory(commandDependencies),
            this.dependencies.messageBus,
            this.dependencies.typeAliasString,
            this.dependencies.stringCombinator,
        )
    }

    override fun testGeneratorQueryHandler(): TestGeneratorQueryHandler {
        return TestGeneratorQueryHandler(this.dependencies.busLocker, this.dependencies.clock)
    }
}
