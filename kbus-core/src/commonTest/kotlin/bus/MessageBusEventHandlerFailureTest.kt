package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.core.fixtures.DelayingStorageEventHandler
import com.jimbroze.kbus.core.fixtures.EventCommand
import com.jimbroze.kbus.core.fixtures.EventCommandHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.ThrowingStorageEventHandler
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.testdoubles.advanceVirtualTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class MessageBusEventHandlerFailureTest {
    @Test
    fun `runs the remaining event handlers when one of them throws`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        createBusWithIntegrationEventHandlers(stores, locator)

        val bus = MessageBus(locator, appScope = backgroundScope)
        val list = mutableListOf<String>()

        bus.execute(EventCommand("test", list))

        advanceVirtualTime(100)

        assertContains(list, "test")
    }

    @Test
    fun `keeps dispatching later events after an event handler throws`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        createBusWithIntegrationEventHandlers(stores, locator)

        val bus = MessageBus(locator, appScope = backgroundScope)

        // First command: triggers a throwing handler
        val list1 = mutableListOf<String>()
        bus.execute(EventCommand("first", list1))
        advanceVirtualTime(100)

        // Second command: scope should still be active, handlers should still execute
        val list2 = mutableListOf<String>()
        bus.execute(EventCommand("second", list2))
        advanceVirtualTime(100)

        assertContains(list2, "second")
    }

    @Test
    fun `stops pending event handlers when its application scope is cancelled`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        stores.commandStore.registerHandlers(
            EventCommand::class,
            listOf(
                CommandHandlerFactory(EventCommandHandler::class) {
                    EventCommandHandler(it.integrationEventPublisher)
                }
            ),
        )
        stores.eventStore.registerHandlers(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(DelayingStorageEventHandler::class) {
                    DelayingStorageEventHandler(5000)
                }
            ),
        )
        locator.integrationEventRegistrar.addEventHandlers(
            StorageEvent::class,
            listOf(DelayingStorageEventHandler::class),
        )

        // A *child* of backgroundScope — not a share of its context, which would reuse its Job and
        // make the cancel below tear down backgroundScope itself — so teardown cancels whatever the
        // bus launched even if the assertions below fail.
        val appScope =
            CoroutineScope(
                SupervisorJob(backgroundScope.coroutineContext[Job]) +
                    StandardTestDispatcher(testScheduler)
            )
        val bus = MessageBus(locator, appScope = appScope)
        val list = mutableListOf<String>()

        bus.execute(EventCommand("test", list))

        // Cancel the root scope before the delayed handler completes
        appScope.cancel()

        advanceVirtualTime(200)

        assertEquals(0, list.size)
    }

    private fun createBusWithIntegrationEventHandlers(
        stores: HandlerFactoryStoreCollection,
        locator: PersistingHandlerLocator,
    ) {
        stores.commandStore.registerHandlers(
            EventCommand::class,
            listOf(
                CommandHandlerFactory(EventCommandHandler::class) {
                    EventCommandHandler(it.integrationEventPublisher)
                }
            ),
        )
        stores.eventStore.registerHandlers(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(ThrowingStorageEventHandler::class) {
                    ThrowingStorageEventHandler()
                },
                EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
            ),
        )
        locator.integrationEventRegistrar.addEventHandlers(
            StorageEvent::class,
            listOf(ThrowingStorageEventHandler::class, PrintEventHandler::class),
        )
    }
}
