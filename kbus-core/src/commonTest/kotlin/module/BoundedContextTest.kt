package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.core.fixtures.OtherPrintEventHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEventHandler
import com.jimbroze.kbus.core.infrastructure.inbox.InMemoryInboxStore
import com.jimbroze.kbus.core.module.inbox.ContextInbox
import com.jimbroze.kbus.core.module.inbox.InboxAckPolicy
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
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The [GeneratedKBusApi] opt-ins here are deliberately per-test rather than on the class: a
 * class-level opt-in would also suppress the gate for the hand-written subscription tests, which
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
        val context =
            BoundedContext(
                BoundedContextId("orders"),
                subscriptions =
                    listOf(
                        subscribe(StorageEvent::class, LoadedEventHandler(PrintEventHandler::class))
                    ),
            )

        assertTrue(context.handlerLocator.subscribedEventTypes().contains(StorageEvent::class))
    }

    @Test
    @OptIn(GeneratedKBusApi::class)
    fun subscribe_registersOnTheUnderlyingLocatorsIntegrationEventMapper() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        BoundedContext(
            BoundedContextId("orders"),
            locator,
            subscriptions =
                listOf(subscribe(StorageEvent::class, LoadedEventHandler(PrintEventHandler::class))),
        )

        assertTrue(locator.subscribedEventTypes().contains(StorageEvent::class))
    }

    @Test
    @OptIn(GeneratedKBusApi::class)
    fun subscribeDomain_registersOnTheUnderlyingLocatorsDomainEventMapper() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        BoundedContext(
            BoundedContextId("orders"),
            locator,
            subscriptions =
                listOf(
                    subscribeDomain(
                        TestDomainEvent::class,
                        LoadedEventHandler(TestDomainEventHandler::class),
                    )
                ),
        )

        assertTrue(locator.subscribedEventTypes().contains(TestDomainEvent::class))
    }

    @Test
    @OptIn(GeneratedKBusApi::class)
    fun subscribe_doesNotRegisterOnAnotherContextsLocator() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val other = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        BoundedContext(
            BoundedContextId("orders"),
            locator,
            subscriptions =
                listOf(subscribe(StorageEvent::class, LoadedEventHandler(PrintEventHandler::class))),
        )

        assertFalse(other.subscribedEventTypes().contains(StorageEvent::class))
    }

    @Test
    fun subscribe_registersHandlerClassesWithoutTheGeneratedApiOptIn() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        BoundedContext(
            BoundedContextId("orders"),
            locator,
            subscriptions = listOf(subscribe(StorageEvent::class, PrintEventHandler::class)),
        )

        assertTrue(locator.subscribedEventTypes().contains(StorageEvent::class))
    }

    @Test
    fun subscribeDomain_registersHandlerClassesWithoutTheGeneratedApiOptIn() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        BoundedContext(
            BoundedContextId("orders"),
            locator,
            subscriptions =
                listOf(subscribeDomain(TestDomainEvent::class, TestDomainEventHandler::class)),
        )

        assertTrue(locator.subscribedEventTypes().contains(TestDomainEvent::class))
    }

    @Test
    fun inbox_isTheOneTheContextConsumesThrough() {
        val declaredInbox = ContextInbox(InMemoryInboxStore(), InboxAckPolicy.HonourEventStrategy)

        val context =
            BoundedContext(BoundedContextId("orders"), PersistingHandlerLocator(), declaredInbox)

        assertSame(declaredInbox, context.inbox)
    }

    @Test
    fun constructor_leavesAContextDeclaringNoInboxWithout() {
        val context = BoundedContext(BoundedContextId("orders"))

        assertNull(context.inbox)
    }

    @Test
    fun subscribe_registersEveryHandlerClassGiven() {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        stores.eventStore.registerHandlers(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                EventHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("other")
                },
            ),
        )

        BoundedContext(
            BoundedContextId("orders"),
            locator,
            subscriptions =
                listOf(
                    subscribe(
                        StorageEvent::class,
                        PrintEventHandler::class,
                        OtherPrintEventHandler::class,
                    )
                ),
        )

        assertEquals(2, locator.handlersFor(StorageEvent("any", mutableListOf())).size)
    }

    @Test
    fun constructor_appliesEverySubscriptionGiven() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())

        BoundedContext(
            BoundedContextId("orders"),
            locator,
            subscriptions =
                listOf(
                    subscribe(StorageEvent::class, PrintEventHandler::class),
                    subscribeDomain(TestDomainEvent::class, TestDomainEventHandler::class),
                ),
        )

        assertTrue(
            locator
                .subscribedEventTypes()
                .containsAll(setOf(StorageEvent::class, TestDomainEvent::class))
        )
    }
}
