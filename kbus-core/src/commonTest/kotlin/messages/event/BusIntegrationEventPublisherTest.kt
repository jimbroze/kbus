package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.core.fixtures.EmptyIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.EmptyMiddlewareInvocationContext
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class BusIntegrationEventPublisherTest {
    private fun createPublisher(
        locator: PersistingHandlerLocator,
        dispatcherScope: CoroutineScope,
    ): BusIntegrationEventPublisher {
        val eventDispatcher =
            EventDispatcher(
                locator::handlersFor,
                emptyList(),
                dispatcherScope,
                invocationContextProvider = { EmptyMiddlewareInvocationContext },
            )
        return BusIntegrationEventPublisher(locator, eventDispatcher)
    }

    @Test
    fun publishing_a_list_of_events_dispatches_each_to_its_registered_handlers_in_order() =
        runTest {
            val stores = HandlerFactoryStoreCollection()
            val locator = PersistingHandlerLocator(stores)
            val results = mutableListOf<String>()

            stores.eventStore.registerHandlers(
                StorageEvent::class,
                listOf(EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() }),
            )
            locator.integrationEventMapper.addEventHandlers(
                StorageEvent::class,
                listOf(PrintEventHandler::class),
            )

            val publisher = createPublisher(locator, this)

            publisher.publish(
                listOf(StorageEvent("first", results), StorageEvent("second", results))
            )
            advanceUntilIdle()

            assertEquals(listOf("first", "second"), results)
        }

    @Test
    fun publishing_an_event_with_no_registered_handlers_does_nothing() = runTest {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val publisher = createPublisher(locator, this)

        publisher.publish(listOf(TestIntegrationEvent("unhandled")))
        advanceUntilIdle()
    }

    @Test
    fun publishing_an_empty_list_does_nothing() = runTest {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val publisher = createPublisher(locator, this)

        publisher.publish(emptyList())
        advanceUntilIdle()
    }

    @Test
    fun empty_integration_event_publisher_publish_is_a_no_op() = runTest {
        EmptyIntegrationEventPublisher.publish(listOf(TestIntegrationEvent("ignored")))
    }
}
