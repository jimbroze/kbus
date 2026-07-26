package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.core.fixtures.OtherStorageEvent
import com.jimbroze.kbus.core.fixtures.OtherStorageEventHandler
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
        return BoundedContext(BoundedContextId.DEFAULT, LocatorSubscriptions(locator), locator) {
            eventDispatcher
        }
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

    private fun registerStorageEventHandler(stores: HandlerFactoryStoreCollection) {
        stores.eventStore.registerHandlers(
            StorageEvent::class,
            listOf(EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() }),
        )
    }

    @Test
    fun appliesTo_isTrue_forAnEventThisContextHasAnIntegrationHandlerFor() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        registerStorageEventHandler(stores)
        locator.integrationEventMapper.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class),
        )

        val context = createContext(locator, this)

        assertTrue(context.appliesTo(StorageEvent("any", mutableListOf())))
    }

    @Test
    fun appliesTo_isFalse_forAnEventThisContextHasNoHandlerFor() = runTest {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val context = createContext(locator, this)

        assertFalse(context.appliesTo(StorageEvent("any", mutableListOf())))
    }

    @Test
    fun appliesTo_reflectsAHandlerRegisteredAfterTheContextWasConstructed() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        val context = createContext(locator, this)

        registerStorageEventHandler(stores)
        locator.integrationEventMapper.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class),
        )

        assertTrue(context.appliesTo(StorageEvent("any", mutableListOf())))
    }

    @Test
    fun appliesTo_isFalse_forAnotherContextsEvent_whenBothShareHandlerStores() = runTest {
        val stores = HandlerFactoryStoreCollection()
        registerStorageEventHandler(stores)
        stores.eventStore.registerHandlers(
            OtherStorageEvent::class,
            listOf(
                EventHandlerFactory(OtherStorageEventHandler::class) { OtherStorageEventHandler() }
            ),
        )

        val thisLocator = PersistingHandlerLocator(stores)
        thisLocator.integrationEventMapper.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class),
        )
        val otherLocator = PersistingHandlerLocator(stores)
        otherLocator.integrationEventMapper.addEventHandlers(
            OtherStorageEvent::class,
            listOf(OtherStorageEventHandler::class),
        )

        val context = createContext(thisLocator, this)

        assertTrue(context.appliesTo(StorageEvent("any", mutableListOf())))
        assertFalse(context.appliesTo(OtherStorageEvent("any", mutableListOf())))
    }
}
