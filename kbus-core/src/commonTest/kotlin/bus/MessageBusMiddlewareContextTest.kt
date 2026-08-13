package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.core.fixtures.CapturingContextMiddleware
import com.jimbroze.kbus.core.fixtures.EventCommand
import com.jimbroze.kbus.core.fixtures.EventCommandHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.ReturnCommand
import com.jimbroze.kbus.core.fixtures.ReturnCommandHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.StorageQuery
import com.jimbroze.kbus.core.fixtures.StorageQueryHandler
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.persisting.store.QueryHandlerFactory
import com.jimbroze.kbus.testdoubles.advanceVirtualTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

class MessageBusMiddlewareContextTest {
    @Test
    fun `gives a command's middleware a publisher that reaches its destinations`() = runTest {
        val stores = createStoresWithStorageEventHandler()
        stores.commandStore.registerHandlers(
            ReturnCommand::class,
            listOf(CommandHandlerFactory(ReturnCommandHandler::class) { ReturnCommandHandler() }),
        )
        val locator = PersistingHandlerLocator(stores)
        locator.integrationEventRegistrar.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class),
        )
        val middleware = CapturingContextMiddleware()
        val bus = MessageBus(locator, middlewares = listOf(middleware), appScope = backgroundScope)

        bus.execute(ReturnCommand("Test the bus"))

        val list = mutableListOf<String>()
        middleware.capturedContext!!
            .integrationEventPublisher
            .publish(listOf(StorageEvent("via-context", list)))
        advanceVirtualTime(100)

        assertContains(list, "via-context")
    }

    @Test
    fun `gives a query's middleware a publisher that reaches its destinations`() = runTest {
        val stores = createStoresWithStorageEventHandler()
        stores.queryStore.registerHandlers(
            StorageQuery::class,
            listOf(QueryHandlerFactory(StorageQueryHandler::class) { StorageQueryHandler() }),
        )
        val locator = PersistingHandlerLocator(stores)
        locator.integrationEventRegistrar.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class),
        )
        val middleware = CapturingContextMiddleware()
        val bus = MessageBus(locator, middlewares = listOf(middleware), appScope = backgroundScope)

        bus.fetch(StorageQuery(0, mutableListOf("Test the bus")))

        val list = mutableListOf<String>()
        middleware.capturedContext!!
            .integrationEventPublisher
            .publish(listOf(StorageEvent("via-context", list)))
        advanceVirtualTime(100)

        assertContains(list, "via-context")
    }

    @Test
    fun `gives an event's middleware a publisher that reaches its destinations`() = runTest {
        val stores = createStoresWithStorageEventHandler()
        stores.commandStore.registerHandlers(
            EventCommand::class,
            listOf(
                CommandHandlerFactory(EventCommandHandler::class) {
                    EventCommandHandler(it.integrationEventPublisher)
                }
            ),
        )
        val locator = PersistingHandlerLocator(stores)
        locator.integrationEventRegistrar.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class),
        )
        val middleware = CapturingContextMiddleware()
        val bus = MessageBus(locator, middlewares = listOf(middleware), appScope = backgroundScope)

        bus.execute(EventCommand("triggering event", mutableListOf()))
        advanceVirtualTime(100)

        // Middleware wraps the StorageEvent's own dispatch, not just the triggering command's.
        val eventContext = middleware.contextFor(StorageEvent::class)
        assertNotNull(eventContext)

        val list = mutableListOf<String>()
        eventContext.integrationEventPublisher.publish(
            listOf(StorageEvent("via-event-context", list))
        )
        advanceVirtualTime(100)

        assertContains(list, "via-event-context")
    }

    private fun createStoresWithStorageEventHandler(): HandlerFactoryStoreCollection {
        val stores = HandlerFactoryStoreCollection()
        stores.eventStore.registerHandlers(
            StorageEvent::class,
            listOf(EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() }),
        )
        return stores
    }
}
