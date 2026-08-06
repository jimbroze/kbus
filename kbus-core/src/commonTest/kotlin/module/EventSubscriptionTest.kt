package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.core.fixtures.OtherPrintEventHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEventHandler
import com.jimbroze.kbus.core.fixtures.noPublishHandlerDependencies
import com.jimbroze.kbus.core.registry.generation.GeneratedKBusApi
import com.jimbroze.kbus.core.registry.generation.LoadedEventHandler
import com.jimbroze.kbus.core.registry.generation.domainSubscription
import com.jimbroze.kbus.core.registry.generation.integrationSubscription
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
    fun integrationSubscription_makesTheEventOneTheLocatorIsSubscribedTo() {
        integrationSubscription(StorageEvent::class, PrintEventHandler::class).registerOn(locator)

        assertTrue(locator.subscribedEventTypes().contains(StorageEvent::class))
    }

    @Test
    fun domainSubscription_makesTheDomainEventOneTheLocatorIsSubscribedTo() {
        domainSubscription(TestDomainEvent::class, TestDomainEventHandler::class)
            .registerOn(locator)

        assertTrue(locator.subscribedEventTypes().contains(TestDomainEvent::class))
    }

    @Test
    fun integrationSubscription_registersEveryHandlerGivenInOneCall() {
        stores.eventStore.registerHandlers(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                EventHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("other")
                },
            ),
        )

        integrationSubscription(
                StorageEvent::class,
                PrintEventHandler::class,
                OtherPrintEventHandler::class,
            )
            .registerOn(locator)

        assertEquals(
            2,
            locator
                .handlersFor(StorageEvent("any", mutableListOf()), noPublishHandlerDependencies)
                .size,
        )
    }

    @Test
    fun integrationSubscription_subscribesToNothingWhenGivenNoHandlers() {
        integrationSubscription(StorageEvent::class).registerOn(locator)

        assertFalse(locator.subscribedEventTypes().contains(StorageEvent::class))
    }

    @Test
    @OptIn(GeneratedKBusApi::class)
    fun integrationSubscription_acceptsLoadedHandlerTokensInPlaceOfHandlerClasses() {
        integrationSubscription(StorageEvent::class, LoadedEventHandler(PrintEventHandler::class))
            .registerOn(locator)

        assertTrue(locator.subscribedEventTypes().contains(StorageEvent::class))
    }

    @Test
    @OptIn(GeneratedKBusApi::class)
    fun domainSubscription_acceptsLoadedHandlerTokensInPlaceOfHandlerClasses() {
        domainSubscription(
                TestDomainEvent::class,
                LoadedEventHandler(TestDomainEventHandler::class),
            )
            .registerOn(locator)

        assertTrue(locator.subscribedEventTypes().contains(TestDomainEvent::class))
    }
}
