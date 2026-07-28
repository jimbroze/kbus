package com.jimbroze.kbus.core.registry.generation

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.fixtures.FailureCommand
import com.jimbroze.kbus.core.fixtures.FailureQuery
import com.jimbroze.kbus.core.fixtures.StorageCommand
import com.jimbroze.kbus.core.fixtures.StorageQuery
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reports fixed module identities for [StorageCommand]/[StorageQuery] only. */
private class FakeGenerationHandlerFactory(
    private val commandModule: String? = null,
    private val queryModule: String? = null,
) : GenerationHandlerFactory {
    override fun <TCommand : Command<TResult>, TResult : KBusResult> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TResult>? = null

    override fun <TQuery : Query<TResult>, TResult : KBusResult> handlerFor(
        query: TQuery
    ): QueryHandler<TQuery, TResult>? = null

    override fun <TEvent : Event> eventHandler(
        handlerClass: KClass<EventHandler<TEvent>>
    ): EventHandler<TEvent>? = null

    override fun commandModule(commandClass: KClass<out Command<*>>): String? =
        if (commandClass == StorageCommand::class) commandModule else null

    override fun queryModule(queryClass: KClass<out Query<*>>): String? =
        if (queryClass == StorageQuery::class) queryModule else null
}

class GenerationHandlerLocatorTest {
    @Test
    fun hasHandlerFor_command_isTrueWhenTheFactoryReportsThisLocatorsIdentity() {
        val locator =
            GenerationHandlerLocator(
                FakeGenerationHandlerFactory(commandModule = "orders"),
                contextIdentity = "orders",
            )

        assertTrue(locator.hasHandlerFor(StorageCommand("test", mutableListOf())))
    }

    @Test
    fun hasHandlerFor_command_isFalseForAnotherContextsIdentity() {
        val locator =
            GenerationHandlerLocator(
                FakeGenerationHandlerFactory(commandModule = "orders"),
                contextIdentity = "inventory",
            )

        assertFalse(locator.hasHandlerFor(StorageCommand("test", mutableListOf())))
    }

    @Test
    fun hasHandlerFor_command_isFalseForACommandTheFactoryDoesNotKnow() {
        val locator =
            GenerationHandlerLocator(FakeGenerationHandlerFactory(commandModule = "orders"))

        assertFalse(locator.hasHandlerFor(FailureCommand()))
    }

    @Test
    fun hasHandlerFor_command_defaultIdentityMatchesUnassignedModule() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory(commandModule = ""))

        assertTrue(locator.hasHandlerFor(StorageCommand("test", mutableListOf())))
    }

    @Test
    fun hasHandlerFor_query_isTrueWhenTheFactoryReportsThisLocatorsIdentity() {
        val locator =
            GenerationHandlerLocator(
                FakeGenerationHandlerFactory(queryModule = "orders"),
                contextIdentity = "orders",
            )

        assertTrue(locator.hasHandlerFor(StorageQuery(0, mutableListOf())))
    }

    @Test
    fun hasHandlerFor_query_isFalseForAnotherContextsIdentity() {
        val locator =
            GenerationHandlerLocator(
                FakeGenerationHandlerFactory(queryModule = "orders"),
                contextIdentity = "inventory",
            )

        assertFalse(locator.hasHandlerFor(StorageQuery(0, mutableListOf())))
    }

    @Test
    fun hasHandlerFor_query_isFalseForAQueryTheFactoryDoesNotKnow() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory(queryModule = "orders"))

        assertFalse(locator.hasHandlerFor(FailureQuery()))
    }

    @Test
    fun hasHandlerFor_query_defaultIdentityMatchesUnassignedModule() {
        val locator = GenerationHandlerLocator(FakeGenerationHandlerFactory(queryModule = ""))

        assertTrue(locator.hasHandlerFor(StorageQuery(0, mutableListOf())))
    }
}
