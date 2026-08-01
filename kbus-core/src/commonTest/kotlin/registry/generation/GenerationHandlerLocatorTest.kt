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
import com.jimbroze.kbus.core.fixtures.testCommandDependencies
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Holds [StorageCommand]/[StorageQuery] under one fixed module identity each, and — like a
 * generated factory shared by several contexts — hands back their handlers whatever the identity.
 */
private class FakeGenerationHandlerFactory(
    private val commandModule: String? = null,
    private val queryModule: String? = null,
) : GenerationHandlerFactory {
    @Suppress("UNCHECKED_CAST")
    override fun <TCommand : Command<TResult>, TResult : KBusResult> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TResult>? =
        if (command is StorageCommand) StorageCommandHandler() as CommandHandler<TCommand, TResult>
        else null

    @Suppress("UNCHECKED_CAST")
    override fun <TQuery : Query<TResult>, TResult : KBusResult> handlerFor(
        query: TQuery
    ): QueryHandler<TQuery, TResult>? =
        if (query is StorageQuery) StorageQueryHandler() as QueryHandler<TQuery, TResult> else null

    override fun <TEvent : Event> eventHandler(
        handlerClass: KClass<EventHandler<TEvent>>
    ): EventHandler<TEvent>? = null

    override fun commandTypesFor(contextIdentity: String): Set<KClass<out Command<*>>> =
        if (contextIdentity == commandModule) setOf(StorageCommand::class) else emptySet()

    override fun queryTypesFor(contextIdentity: String): Set<KClass<out Query<*>>> =
        if (contextIdentity == queryModule) setOf(StorageQuery::class) else emptySet()
}

class GenerationHandlerLocatorTest {
    @Test
    fun handledCommandTypes_areTheFactorysCommandsForThisLocatorsIdentity() {
        val locator =
            GenerationHandlerLocator(
                FakeGenerationHandlerFactory(commandModule = "orders"),
                contextIdentity = "orders",
            )

        assertEquals(setOf(StorageCommand::class), locator.handledCommandTypes())
    }

    @Test
    fun handledCommandTypes_excludeAnotherContextsCommands() {
        val locator =
            GenerationHandlerLocator(
                FakeGenerationHandlerFactory(commandModule = "orders"),
                contextIdentity = "inventory",
            )

        assertEquals(emptySet(), locator.handledCommandTypes())
    }

    @Test
    fun handledCommandTypes_defaultIdentityMatchesUnassignedModule() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory(commandModule = ""))

        assertEquals(setOf(StorageCommand::class), locator.handledCommandTypes())
    }

    @Test
    fun handledQueryTypes_areTheFactorysQueriesForThisLocatorsIdentity() {
        val locator =
            GenerationHandlerLocator(
                FakeGenerationHandlerFactory(queryModule = "orders"),
                contextIdentity = "orders",
            )

        assertEquals(setOf(StorageQuery::class), locator.handledQueryTypes())
    }

    @Test
    fun handledQueryTypes_excludeAnotherContextsQueries() {
        val locator =
            GenerationHandlerLocator(
                FakeGenerationHandlerFactory(queryModule = "orders"),
                contextIdentity = "inventory",
            )

        assertEquals(emptySet(), locator.handledQueryTypes())
    }

    @Test
    fun handledQueryTypes_defaultIdentityMatchesUnassignedModule() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory(queryModule = ""))

        assertEquals(setOf(StorageQuery::class), locator.handledQueryTypes())
    }

    @Test
    fun handlerFor_findsACommandThisContextOwns() {
        val locator =
            GenerationHandlerLocator(
                FakeGenerationHandlerFactory(commandModule = "orders"),
                contextIdentity = "orders",
            )

        val handler =
            locator.handlerFor(
                StorageCommand("test", mutableListOf()),
                testCommandDependencies<Any?>(),
            )

        assertIs<StorageCommandHandler>(handler)
    }

    @Test
    fun handlerFor_doesNotFindACommandAnotherContextOwns() {
        val locator =
            GenerationHandlerLocator(
                FakeGenerationHandlerFactory(commandModule = "orders"),
                contextIdentity = "inventory",
            )

        val handler =
            locator.handlerFor(
                StorageCommand("test", mutableListOf()),
                testCommandDependencies<Any?>(),
            )

        assertNull(handler)
    }

    @Test
    fun handlerFor_findsAQueryThisContextOwns() {
        val locator =
            GenerationHandlerLocator(
                FakeGenerationHandlerFactory(queryModule = "orders"),
                contextIdentity = "orders",
            )

        assertIs<StorageQueryHandler>(locator.handlerFor(StorageQuery(0, mutableListOf())))
    }

    @Test
    fun handlerFor_doesNotFindAQueryAnotherContextOwns() {
        val locator =
            GenerationHandlerLocator(
                FakeGenerationHandlerFactory(queryModule = "orders"),
                contextIdentity = "inventory",
            )

        assertNull(locator.handlerFor(StorageQuery(0, mutableListOf())))
    }
}
