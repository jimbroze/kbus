package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.contracts.result.FailureReason
import com.jimbroze.kbus.core.fixtures.BrokenStateFailureCommandHandler
import com.jimbroze.kbus.core.fixtures.CapturingContextMiddleware
import com.jimbroze.kbus.core.fixtures.DelayingStorageEventHandler
import com.jimbroze.kbus.core.fixtures.EventCommand
import com.jimbroze.kbus.core.fixtures.EventCommandHandler
import com.jimbroze.kbus.core.fixtures.FailureCommand
import com.jimbroze.kbus.core.fixtures.FailureCommandFailure
import com.jimbroze.kbus.core.fixtures.FailureQuery
import com.jimbroze.kbus.core.fixtures.FailureQueryHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.ReturnCommand
import com.jimbroze.kbus.core.fixtures.ReturnCommandHandler
import com.jimbroze.kbus.core.fixtures.StorageCommand
import com.jimbroze.kbus.core.fixtures.StorageCommandHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.StorageQuery
import com.jimbroze.kbus.core.fixtures.StorageQueryHandler
import com.jimbroze.kbus.core.fixtures.ThrowingStorageEventHandler
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.persisting.store.QueryHandlerFactory
import com.jimbroze.kbus.testdoubles.advanceVirtualTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class MessageBusTest {
    @Test
    fun test_execute_executes_a_command_successfully() = runTest {
        val stores = HandlerFactoryStoreCollection()
        stores.commandStore.registerHandlers(
            StorageCommand::class,
            listOf(CommandHandlerFactory(StorageCommandHandler::class) { StorageCommandHandler() }),
        )

        val bus = MessageBus(PersistingHandlerLocator(stores))
        val list = mutableListOf<String>()

        val result = bus.execute(StorageCommand("Test the bus", list))

        assertTrue(result.isSuccess)
        assertContains(list, "Test the bus")
    }

    @Test
    fun test_command_can_return_a_success_value() = runTest {
        val stores = HandlerFactoryStoreCollection()
        stores.commandStore.registerHandlers(
            ReturnCommand::class,
            listOf(CommandHandlerFactory(ReturnCommandHandler::class) { ReturnCommandHandler() }),
        )

        val bus = MessageBus(PersistingHandlerLocator(stores))

        val result = bus.execute(ReturnCommand("Test the bus"))

        assertTrue(result.isSuccess)
        assertEquals("Test the bus", result.getOrNull())
    }

    @Test
    fun test_failure_will_return_exception_if_provided() = runTest {
        val stores = HandlerFactoryStoreCollection()
        stores.commandStore.registerHandlers(
            FailureCommand::class,
            listOf(
                CommandHandlerFactory(BrokenStateFailureCommandHandler::class) {
                    BrokenStateFailureCommandHandler()
                }
            ),
        )

        val bus = MessageBus(PersistingHandlerLocator(stores))

        val result = bus.execute(FailureCommand())

        assertTrue(result.isFailure)
        val failure = result.failureOrNull()
        assertIs<FailureCommandFailure>(failure)
        assertEquals("Illegal state in command handling", failure.reason.message)
        assertEquals("Failure: Illegal state in command handling", result.toString())
    }

    @Test
    fun test_executed_query_returns_a_successful_result_value() = runTest {
        val stores = HandlerFactoryStoreCollection()
        stores.queryStore.registerHandlers(
            StorageQuery::class,
            listOf(QueryHandlerFactory(StorageQueryHandler::class) { StorageQueryHandler() }),
        )

        val bus = MessageBus(PersistingHandlerLocator(stores))
        val list = mutableListOf("Test the bus")

        val result = bus.fetch(StorageQuery(0, list))

        assertTrue(result.isSuccess)
        assertEquals("Test the bus", result.getOrNull())
    }

    @Test
    fun test_resultFailure_exception_in_query_returns_failure() = runTest {
        val stores = HandlerFactoryStoreCollection()
        stores.queryStore.registerHandlers(
            FailureQuery::class,
            listOf(QueryHandlerFactory(FailureQueryHandler::class) { FailureQueryHandler() }),
        )

        val bus = MessageBus(PersistingHandlerLocator(stores))

        val result = bus.fetch(FailureQuery())

        assertTrue(result.isFailure)
        val failure = result.failureOrNull()
        assertNotNull(failure)
        assertIs<FailureReason>(failure.reason)
        assertEquals("The query failed", failure.reason.message)
    }

    private fun createBusWithIntegrationEventHandlers(
        stores: HandlerFactoryStoreCollection,
        locator: PersistingHandlerLocator,
    ) {
        stores.commandStore.registerHandlers(
            EventCommand::class,
            listOf(CommandHandlerFactory(EventCommandHandler::class) { EventCommandHandler() }),
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
        locator.integrationEventMapper.addEventHandlers(
            StorageEvent::class,
            listOf(ThrowingStorageEventHandler::class, PrintEventHandler::class),
        )
    }

    @Test
    fun test_failing_integration_event_handler_does_not_prevent_other_handlers() = runTest {
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
    fun test_failing_handler_does_not_cancel_bus_event_dispatcher_scope() = runTest {
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
    fun test_cancelling_bus_appScope_stops_pending_event_handlers() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        stores.commandStore.registerHandlers(
            EventCommand::class,
            listOf(CommandHandlerFactory(EventCommandHandler::class) { EventCommandHandler() }),
        )
        stores.eventStore.registerHandlers(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(DelayingStorageEventHandler::class) {
                    DelayingStorageEventHandler(5000)
                }
            ),
        )
        locator.integrationEventMapper.addEventHandlers(
            StorageEvent::class,
            listOf(DelayingStorageEventHandler::class),
        )

        val appScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val bus = MessageBus(locator, appScope = appScope)
        val list = mutableListOf<String>()

        bus.execute(EventCommand("test", list))

        // Cancel the root scope before the delayed handler completes
        appScope.cancel()

        advanceVirtualTime(200)

        assertEquals(0, list.size)
    }

    private fun createStoresWithStorageEventHandler(): HandlerFactoryStoreCollection {
        val stores = HandlerFactoryStoreCollection()
        stores.eventStore.registerHandlers(
            StorageEvent::class,
            listOf(EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() }),
        )
        return stores
    }

    @Test
    fun test_command_middleware_context_carries_a_working_integration_event_publisher() = runTest {
        val stores = createStoresWithStorageEventHandler()
        stores.commandStore.registerHandlers(
            ReturnCommand::class,
            listOf(CommandHandlerFactory(ReturnCommandHandler::class) { ReturnCommandHandler() }),
        )
        val locator = PersistingHandlerLocator(stores)
        locator.integrationEventMapper.addEventHandlers(
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
    fun test_query_middleware_context_carries_a_working_integration_event_publisher() = runTest {
        val stores = createStoresWithStorageEventHandler()
        stores.queryStore.registerHandlers(
            StorageQuery::class,
            listOf(QueryHandlerFactory(StorageQueryHandler::class) { StorageQueryHandler() }),
        )
        val locator = PersistingHandlerLocator(stores)
        locator.integrationEventMapper.addEventHandlers(
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
    fun test_event_dispatch_middleware_context_carries_a_working_integration_event_publisher() =
        runTest {
            val stores = createStoresWithStorageEventHandler()
            stores.commandStore.registerHandlers(
                EventCommand::class,
                listOf(CommandHandlerFactory(EventCommandHandler::class) { EventCommandHandler() }),
            )
            val locator = PersistingHandlerLocator(stores)
            locator.integrationEventMapper.addEventHandlers(
                StorageEvent::class,
                listOf(PrintEventHandler::class),
            )
            val middleware = CapturingContextMiddleware()
            val bus =
                MessageBus(locator, middlewares = listOf(middleware), appScope = backgroundScope)

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
}
