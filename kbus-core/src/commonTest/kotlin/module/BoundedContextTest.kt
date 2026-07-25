package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import com.jimbroze.kbus.core.fixtures.emptyContextFactory
import com.jimbroze.kbus.core.messages.event.dispatch.EventDispatcher
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
class BoundedContextTest {
    private fun createContext(
        locator: PersistingHandlerLocator,
        dispatcherScope: CoroutineScope,
    ): BoundedContext {
        val eventDispatcher =
            EventDispatcher(
                locator::handlersFor,
                emptyList(),
                dispatcherScope,
                contextFactory = emptyContextFactory(),
            )
        return BoundedContext("default", locator) { eventDispatcher }
    }

    @Test
    fun delivering_a_list_of_envelopes_dispatches_each_to_its_registered_handlers_in_order() =
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

            val context = createContext(locator, this)

            context.deliver(
                listOf(
                    EventEnvelope.of(StorageEvent("first", results)),
                    EventEnvelope.of(StorageEvent("second", results)),
                )
            )
            advanceUntilIdle()

            assertEquals(listOf("first", "second"), results)
        }

    @Test
    fun delivering_an_event_with_no_registered_handlers_does_nothing() = runTest {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val context = createContext(locator, this)

        context.deliver(listOf(EventEnvelope.of(TestIntegrationEvent("unhandled"))))
        advanceUntilIdle()
    }

    @Test
    fun delivering_an_empty_list_does_nothing() = runTest {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val context = createContext(locator, this)

        context.deliver(emptyList())
        advanceUntilIdle()
    }
}
