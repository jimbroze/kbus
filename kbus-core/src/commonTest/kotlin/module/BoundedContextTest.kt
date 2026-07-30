package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.core.fixtures.OtherPrintEventHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEventHandler
import com.jimbroze.kbus.core.registry.GeneratedKBusApi
import com.jimbroze.kbus.core.registry.LoadedEventHandler
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The [GeneratedKBusApi] opt-ins here are deliberately per-test rather than on the class: a
 * class-level opt-in would also suppress the gate for the hand-written registration tests, which
 * exist precisely to prove that path needs no opt-in.
 */
class BoundedContextTest {
    @Test
    fun id_isTheOneItWasConstructedWith() {
        val context = BoundedContext(BoundedContextId("orders"))

        assertEquals(BoundedContextId("orders"), context.id)
    }

    @Test
    @OptIn(GeneratedKBusApi::class)
    fun constructor_defaultsToAFreshPersistingHandlerLocator() {
        val context = BoundedContext(BoundedContextId("orders"))

        context.addEventHandlers(
            StorageEvent::class,
            listOf(LoadedEventHandler(PrintEventHandler::class)),
        )

        assertTrue(context.handlerLocator.hasHandlersFor(StorageEvent("any", mutableListOf())))
    }

    @Test
    @OptIn(GeneratedKBusApi::class)
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
    @OptIn(GeneratedKBusApi::class)
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
    @OptIn(GeneratedKBusApi::class)
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

    @Test
    fun addEventHandlers_registersHandlerClassesWithoutTheGeneratedApiOptIn() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val context = BoundedContext(BoundedContextId("orders"), locator)

        context.addEventHandlers(StorageEvent::class, PrintEventHandler::class)

        assertTrue(locator.hasHandlersFor(StorageEvent("any", mutableListOf())))
    }

    @Test
    fun addDomainHandlers_registersHandlerClassesWithoutTheGeneratedApiOptIn() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val context = BoundedContext(BoundedContextId("orders"), locator)

        context.addDomainHandlers(TestDomainEvent::class, TestDomainEventHandler::class)

        assertTrue(locator.hasHandlersFor(TestDomainEvent("any")))
    }

    @Test
    fun addEventHandlers_registersEveryHandlerClassGiven() {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        val context = BoundedContext(BoundedContextId("orders"), locator)
        stores.eventStore.registerHandlers(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                EventHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("other")
                },
            ),
        )

        context.addEventHandlers(
            StorageEvent::class,
            PrintEventHandler::class,
            OtherPrintEventHandler::class,
        )

        assertEquals(2, locator.handlersFor(StorageEvent("any", mutableListOf())).size)
    }
}
