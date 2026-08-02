package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.core.fixtures.OtherPrintEventHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEventHandler
import com.jimbroze.kbus.core.registry.generation.GeneratedKBusApi
import com.jimbroze.kbus.core.registry.generation.LoadedEventHandler
import com.jimbroze.kbus.core.registry.generation.subscribe
import com.jimbroze.kbus.core.registry.generation.subscribeDomain
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventSubscriptionTest {
    private val stores = HandlerFactoryStoreCollection()
    private val locator = PersistingHandlerLocator(stores)

    @Test
    fun subscribe_makesTheEventOneTheLocatorIsSubscribedTo() {
        subscribe(StorageEvent::class, PrintEventHandler::class).registerOn(locator)

        assertTrue(locator.subscribedEventTypes().contains(StorageEvent::class))
    }

    @Test
    fun subscribeDomain_makesTheDomainEventOneTheLocatorIsSubscribedTo() {
        subscribeDomain(TestDomainEvent::class, TestDomainEventHandler::class).registerOn(locator)

        assertTrue(locator.subscribedEventTypes().contains(TestDomainEvent::class))
    }

    @Test
    fun subscribe_registersEveryHandlerGivenInOneCall() {
        stores.eventStore.registerHandlers(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                EventHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("other")
                },
            ),
        )

        subscribe(StorageEvent::class, PrintEventHandler::class, OtherPrintEventHandler::class)
            .registerOn(locator)

        assertEquals(2, locator.handlersFor(StorageEvent("any", mutableListOf())).size)
    }

    @Test
    fun subscribe_subscribesToNothingWhenGivenNoHandlers() {
        subscribe(StorageEvent::class).registerOn(locator)

        assertFalse(locator.subscribedEventTypes().contains(StorageEvent::class))
    }

    @Test
    @OptIn(GeneratedKBusApi::class)
    fun subscribe_acceptsLoadedHandlerTokensInPlaceOfHandlerClasses() {
        subscribe(StorageEvent::class, LoadedEventHandler(PrintEventHandler::class))
            .registerOn(locator)

        assertTrue(locator.subscribedEventTypes().contains(StorageEvent::class))
    }

    @Test
    @OptIn(GeneratedKBusApi::class)
    fun subscribeDomain_acceptsLoadedHandlerTokensInPlaceOfHandlerClasses() {
        subscribeDomain(TestDomainEvent::class, LoadedEventHandler(TestDomainEventHandler::class))
            .registerOn(locator)

        assertTrue(locator.subscribedEventTypes().contains(TestDomainEvent::class))
    }
}
