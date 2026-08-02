package com.jimbroze.kbus.core.registry.generation

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.fixtures.StorageCommand
import com.jimbroze.kbus.core.fixtures.StorageCommandHandler
import com.jimbroze.kbus.core.fixtures.StorageQuery
import com.jimbroze.kbus.core.fixtures.StorageQueryHandler
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEventHandler
import com.jimbroze.kbus.core.fixtures.noPublishHandlerDependencies
import com.jimbroze.kbus.core.fixtures.testCommandDependencies
import com.jimbroze.kbus.core.messages.HandlerDependencies
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/** One bounded context's generated factory: [StorageCommand] and [StorageQuery] only. */
private class FakeGenerationHandlerFactory(
    private val holdsCommand: Boolean = false,
    private val holdsQuery: Boolean = false,
    private val holdsDomainEventHandler: Boolean = false,
) : GenerationHandlerFactory {
    @Suppress("UNCHECKED_CAST")
    override fun <TCommand : Command<TResult>, TResult : KBusResult> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TResult>? =
        if (holdsCommand && command is StorageCommand)
            StorageCommandHandler() as CommandHandler<TCommand, TResult>
        else null

    @Suppress("UNCHECKED_CAST")
    override fun <TQuery : Query<TResult>, TResult : KBusResult> handlerFor(
        query: TQuery
    ): QueryHandler<TQuery, TResult>? =
        if (holdsQuery && query is StorageQuery)
            StorageQueryHandler() as QueryHandler<TQuery, TResult>
        else null

    override fun <TEvent : Event> eventHandler(
        handlerClass: KClass<EventHandler<TEvent>>,
        handlerDependencies: HandlerDependencies,
    ): EventHandler<TEvent>? = null

    /** What the locator handed this factory the last time it built a domain handler. */
    var dependenciesGivenToDomainHandler: HandlerDependencies? = null
        private set

    @Suppress("UNCHECKED_CAST")
    override fun <TEvent : DomainEvent> domainEventHandler(
        handlerClass: KClass<DomainEventHandler<TEvent>>,
        handlerDependencies: HandlerDependencies,
    ): DomainEventHandler<TEvent>? {
        if (!holdsDomainEventHandler || handlerClass != TestDomainEventHandler::class) return null
        dependenciesGivenToDomainHandler = handlerDependencies
        return TestDomainEventHandler(mutableListOf()) as DomainEventHandler<TEvent>
    }

    override fun commandTypes(): Set<KClass<out Command<*>>> =
        if (holdsCommand) setOf(StorageCommand::class) else emptySet()

    override fun queryTypes(): Set<KClass<out Query<*>>> =
        if (holdsQuery) setOf(StorageQuery::class) else emptySet()
}

class GenerationHandlerLocatorTest {
    @Test
    fun handledCommandTypes_areItsFactorysCommands() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory(holdsCommand = true))

        assertEquals(setOf(StorageCommand::class), locator.handledCommandTypes())
    }

    @Test
    fun handledCommandTypes_areEmptyForAContextHoldingNoCommands() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory())

        assertEquals(emptySet(), locator.handledCommandTypes())
    }

    @Test
    fun handledQueryTypes_areItsFactorysQueries() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory(holdsQuery = true))

        assertEquals(setOf(StorageQuery::class), locator.handledQueryTypes())
    }

    @Test
    fun handledQueryTypes_areEmptyForAContextHoldingNoQueries() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory())

        assertEquals(emptySet(), locator.handledQueryTypes())
    }

    @Test
    fun handlerFor_findsACommandThisContextOwns() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory(holdsCommand = true))

        val handler =
            locator.handlerFor(
                StorageCommand("test", mutableListOf()),
                testCommandDependencies<Any?>(),
            )

        assertIs<StorageCommandHandler>(handler)
    }

    @Test
    fun handlerFor_doesNotFindACommandAnotherContextOwns() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory())

        val handler =
            locator.handlerFor(
                StorageCommand("test", mutableListOf()),
                testCommandDependencies<Any?>(),
            )

        assertNull(handler)
    }

    @Test
    fun handlerFor_findsAQueryThisContextOwns() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory(holdsQuery = true))

        assertIs<StorageQueryHandler>(locator.handlerFor(StorageQuery(0, mutableListOf())))
    }

    @Test
    fun handlerFor_doesNotFindAQueryAnotherContextOwns() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory())

        assertNull(locator.handlerFor(StorageQuery(0, mutableListOf())))
    }

    @Test
    fun domainHandlersFor_buildsTheSubscribedHandlersFromItsFactory() {
        val locator =
            GenerationHandlerLocator(FakeGenerationHandlerFactory(holdsDomainEventHandler = true))
        locator.domainEventMapper.addDomainHandlers(
            TestDomainEvent::class,
            listOf(TestDomainEventHandler::class),
        )

        val handlers =
            locator.domainHandlersFor(TestDomainEvent("test"), noPublishHandlerDependencies)

        assertEquals(1, handlers.size)
        assertIs<TestDomainEventHandler>(handlers.single())
    }

    @Test
    fun domainHandlersFor_buildsEachHandlerWithTheDependenciesItWasGiven() {
        val factory = FakeGenerationHandlerFactory(holdsDomainEventHandler = true)
        val locator = GenerationHandlerLocator(factory)
        locator.domainEventMapper.addDomainHandlers(
            TestDomainEvent::class,
            listOf(TestDomainEventHandler::class),
        )

        locator.domainHandlersFor(TestDomainEvent("test"), noPublishHandlerDependencies)

        assertSame(noPublishHandlerDependencies, factory.dependenciesGivenToDomainHandler)
    }

    @Test
    fun domainHandlersFor_findsNoHandlersForAnUnsubscribedEvent() {
        val locator =
            GenerationHandlerLocator(FakeGenerationHandlerFactory(holdsDomainEventHandler = true))

        assertEquals(
            emptyList(),
            locator.domainHandlersFor(TestDomainEvent("test"), noPublishHandlerDependencies),
        )
    }

    @Test
    fun domainHandlersFor_failsWhenTheContextsFactoryCannotBuildASubscribedHandler() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory())
        locator.domainEventMapper.addDomainHandlers(
            TestDomainEvent::class,
            listOf(TestDomainEventHandler::class),
        )

        assertFailsWith<IllegalStateException> {
            locator.domainHandlersFor(TestDomainEvent("test"), noPublishHandlerDependencies)
        }
    }
}
