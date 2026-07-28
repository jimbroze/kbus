package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.Event
import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEventHandler
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.registry.GeneratedKBusApi
import com.jimbroze.kbus.core.registry.LoadedEventHandler
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(GeneratedKBusApi::class)
class BoundedContextTest {
    @Test
    fun id_isTheOneItWasConstructedWith() {
        val context = BoundedContext(BoundedContextId("orders"))

        assertEquals(BoundedContextId("orders"), context.id)
    }

    @Test
    fun constructor_defaultsToAFreshPersistingHandlerLocator() {
        val context = BoundedContext(BoundedContextId("orders"))

        context.addEventHandlers(
            StorageEvent::class,
            listOf(LoadedEventHandler(PrintEventHandler::class)),
        )

        assertTrue(context.handlerLocator.hasHandlersFor(StorageEvent("any", mutableListOf())))
    }

    @Test
    fun constructor_rejectsAHandlerLocatorThatIsNotAnEventMapperProvider() {
        assertFailsWith<IllegalArgumentException> {
            BoundedContext(BoundedContextId("orders"), NonMapperHandlerLocator)
        }
    }

    @Test
    fun addEventHandlers_registersOnTheUnderlyingLocatorsIntegrationEventMapper() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val context = BoundedContext(BoundedContextId("orders"), locator)

        context.addEventHandlers(
            StorageEvent::class,
            listOf(LoadedEventHandler(PrintEventHandler::class)),
        )

        assertTrue(locator.hasHandlersFor(StorageEvent("any", mutableListOf())))
    }

    @Test
    fun addDomainHandlers_registersOnTheUnderlyingLocatorsDomainEventMapper() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val context = BoundedContext(BoundedContextId("orders"), locator)

        context.addDomainHandlers(
            TestDomainEvent::class,
            listOf(LoadedEventHandler(TestDomainEventHandler::class)),
        )

        assertTrue(locator.hasHandlersFor(TestDomainEvent("any")))
    }

    @Test
    fun addEventHandlers_doesNotRegisterOnAnotherContextsLocator() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val other = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val context = BoundedContext(BoundedContextId("orders"), locator)

        context.addEventHandlers(
            StorageEvent::class,
            listOf(LoadedEventHandler(PrintEventHandler::class)),
        )

        assertFalse(other.hasHandlersFor(StorageEvent("any", mutableListOf())))
    }
}

private object NonMapperHandlerLocator : com.jimbroze.kbus.core.registry.HandlerLocator {
    override fun <TCommand : Command<TResult>, TResult : KBusResult> handlerFor(
        command: TCommand,
        commandDependencies: CommandDependencies,
    ): CommandHandler<TCommand, TResult>? = null

    override fun <TQuery : Query<TResult>, TResult : KBusResult> handlerFor(
        query: TQuery
    ): QueryHandler<TQuery, TResult>? = null

    override fun <TEvent : Event> handlersFor(event: TEvent): List<EventHandler<TEvent>> =
        emptyList()

    override fun hasHandlersFor(event: Event): Boolean = false

    override fun hasHandlerFor(command: Command<*>): Boolean = false

    override fun hasHandlerFor(query: Query<*>): Boolean = false
}
