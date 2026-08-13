package com.jimbroze.kbus.core.registry.generation

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.api.messages.event.Event
import com.jimbroze.kbus.api.messages.event.EventHandler
import com.jimbroze.kbus.api.messages.query.Query
import com.jimbroze.kbus.api.messages.query.QueryHandler
import com.jimbroze.kbus.api.result.KBusResult
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
    fun `reports the commands its factory holds`() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory(holdsCommand = true))

        assertEquals(setOf(StorageCommand::class), locator.handledCommandTypes())
    }

    @Test
    fun `reports no commands for a context holding none`() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory())

        assertEquals(emptySet(), locator.handledCommandTypes())
    }

    @Test
    fun `reports the queries its factory holds`() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory(holdsQuery = true))

        assertEquals(setOf(StorageQuery::class), locator.handledQueryTypes())
    }

    @Test
    fun `reports no queries for a context holding none`() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory())

        assertEquals(emptySet(), locator.handledQueryTypes())
    }

    @Test
    fun `builds the handler for a command its context owns`() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory(holdsCommand = true))

        val handler =
            locator.handlerFor(
                StorageCommand("test", mutableListOf()),
                testCommandDependencies<Any?>(),
            )

        assertIs<StorageCommandHandler>(handler)
    }

    @Test
    fun `finds no handler for a command another context owns`() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory())

        val handler =
            locator.handlerFor(
                StorageCommand("test", mutableListOf()),
                testCommandDependencies<Any?>(),
            )

        assertNull(handler)
    }

    @Test
    fun `builds the handler for a query its context owns`() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory(holdsQuery = true))

        assertIs<StorageQueryHandler>(locator.handlerFor(StorageQuery(0, mutableListOf())))
    }

    @Test
    fun `finds no handler for a query another context owns`() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory())

        assertNull(locator.handlerFor(StorageQuery(0, mutableListOf())))
    }

    @Test
    fun `builds the domain handlers its context subscribes to`() {
        val locator =
            GenerationHandlerLocator(FakeGenerationHandlerFactory(holdsDomainEventHandler = true))
        locator.domainEventRegistrar.addDomainHandlers(
            TestDomainEvent::class,
            listOf(TestDomainEventHandler::class),
        )

        val handlers =
            locator.domainHandlersFor(TestDomainEvent("test"), noPublishHandlerDependencies)

        assertEquals(1, handlers.size)
        assertIs<TestDomainEventHandler>(handlers.single())
    }

    @Test
    fun `builds each domain handler with the dependencies it was given`() {
        val factory = FakeGenerationHandlerFactory(holdsDomainEventHandler = true)
        val locator = GenerationHandlerLocator(factory)
        locator.domainEventRegistrar.addDomainHandlers(
            TestDomainEvent::class,
            listOf(TestDomainEventHandler::class),
        )

        locator.domainHandlersFor(TestDomainEvent("test"), noPublishHandlerDependencies)

        assertSame(noPublishHandlerDependencies, factory.dependenciesGivenToDomainHandler)
    }

    @Test
    fun `finds no domain handlers for an event its context does not subscribe to`() {
        val locator =
            GenerationHandlerLocator(FakeGenerationHandlerFactory(holdsDomainEventHandler = true))

        assertEquals(
            emptyList(),
            locator.domainHandlersFor(TestDomainEvent("test"), noPublishHandlerDependencies),
        )
    }

    @Test
    fun `fails when its factory cannot build a handler the context subscribes to`() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory())
        locator.domainEventRegistrar.addDomainHandlers(
            TestDomainEvent::class,
            listOf(TestDomainEventHandler::class),
        )

        assertFailsWith<IllegalStateException> {
            locator.domainHandlersFor(TestDomainEvent("test"), noPublishHandlerDependencies)
        }
    }
}
