package com.jimbroze.kbus.core.boundedcontext

import com.jimbroze.kbus.core.boundedcontext.inbox.BoundedContextInbox
import com.jimbroze.kbus.core.boundedcontext.inbox.InboxAckPolicy
import com.jimbroze.kbus.core.fixtures.OtherPrintEventHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEventHandler
import com.jimbroze.kbus.core.fixtures.noPublishHandlerDependencies
import com.jimbroze.kbus.core.infrastructure.inbox.InMemoryInboxStore
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
    fun `keeps the id it was constructed with`() {
        val context = BoundedContext(BoundedContextId("orders"))

        assertEquals(BoundedContextId("orders"), context.id)
    }

    @Test
    @OptIn(GeneratedKBusApi::class)
    fun `locates handlers through a fresh persisting locator when given none`() {
        val context =
            BoundedContext(
                BoundedContextId("orders"),
                integrationSubscriptions =
                    listOf(
                        integrationSubscription(
                            StorageEvent::class,
                            LoadedEventHandler(PrintEventHandler::class),
                        )
                    ),
            )

        assertTrue(context.handlerLocator.subscribedEventTypes().contains(StorageEvent::class))
    }

    @Test
    @OptIn(GeneratedKBusApi::class)
    fun `registers an integration subscription on its own locator's event mapper`() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        BoundedContext(
            BoundedContextId("orders"),
            locator,
            integrationSubscriptions =
                listOf(
                    integrationSubscription(
                        StorageEvent::class,
                        LoadedEventHandler(PrintEventHandler::class),
                    )
                ),
        )

        assertTrue(locator.subscribedEventTypes().contains(StorageEvent::class))
    }

    @Test
    @OptIn(GeneratedKBusApi::class)
    fun `registers a domain subscription on its own locator's event mapper`() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        BoundedContext(
            BoundedContextId("orders"),
            locator,
            domainSubscriptions =
                listOf(
                    domainSubscription(
                        TestDomainEvent::class,
                        LoadedEventHandler(TestDomainEventHandler::class),
                    )
                ),
        )

        assertTrue(locator.subscribedEventTypes().contains(TestDomainEvent::class))
    }

    @Test
    @OptIn(GeneratedKBusApi::class)
    fun `registers nothing on another context's locator`() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val other = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        BoundedContext(
            BoundedContextId("orders"),
            locator,
            integrationSubscriptions =
                listOf(
                    integrationSubscription(
                        StorageEvent::class,
                        LoadedEventHandler(PrintEventHandler::class),
                    )
                ),
        )

        assertFalse(other.subscribedEventTypes().contains(StorageEvent::class))
    }

    @Test
    fun `registers integration handler classes without opting into the generated API`() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        BoundedContext(
            BoundedContextId("orders"),
            locator,
            integrationSubscriptions =
                listOf(integrationSubscription(StorageEvent::class, PrintEventHandler::class)),
        )

        assertTrue(locator.subscribedEventTypes().contains(StorageEvent::class))
    }

    @Test
    fun `registers domain handler classes without opting into the generated API`() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        BoundedContext(
            BoundedContextId("orders"),
            locator,
            domainSubscriptions =
                listOf(domainSubscription(TestDomainEvent::class, TestDomainEventHandler::class)),
        )

        assertTrue(locator.subscribedEventTypes().contains(TestDomainEvent::class))
    }

    @Test
    fun `consumes through the inbox it was declared with`() {
        val declaredInbox =
            BoundedContextInbox(InMemoryInboxStore(), InboxAckPolicy.HonourEventStrategy)

        val context =
            BoundedContext(BoundedContextId("orders"), PersistingHandlerLocator(), declaredInbox)

        assertSame(declaredInbox, context.inbox)
    }

    @Test
    fun `has no inbox when it declares none`() {
        val context = BoundedContext(BoundedContextId("orders"))

        assertNull(context.inbox)
    }

    @Test
    fun `registers every handler class a subscription names`() {
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
            integrationSubscriptions =
                listOf(
                    integrationSubscription(
                        StorageEvent::class,
                        PrintEventHandler::class,
                        OtherPrintEventHandler::class,
                    )
                ),
        )

        assertEquals(
            2,
            locator
                .handlersFor(StorageEvent("any", mutableListOf()), noPublishHandlerDependencies)
                .size,
        )
    }

    @Test
    fun `applies every subscription it was declared with`() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())

        BoundedContext(
            BoundedContextId("orders"),
            locator,
            domainSubscriptions =
                listOf(domainSubscription(TestDomainEvent::class, TestDomainEventHandler::class)),
            integrationSubscriptions =
                listOf(integrationSubscription(StorageEvent::class, PrintEventHandler::class)),
        )

        assertTrue(
            locator
                .subscribedEventTypes()
                .containsAll(setOf(StorageEvent::class, TestDomainEvent::class))
        )
    }
}
