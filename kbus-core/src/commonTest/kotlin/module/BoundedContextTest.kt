package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEventHandler
import com.jimbroze.kbus.core.registry.GeneratedKBusApi
import com.jimbroze.kbus.core.registry.LoadedEventHandler
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import kotlin.test.Test
import kotlin.test.assertEquals
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
