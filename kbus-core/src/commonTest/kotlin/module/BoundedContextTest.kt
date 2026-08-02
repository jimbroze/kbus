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
import com.jimbroze.kbus.core.registry.generation.addDomainHandlers
import com.jimbroze.kbus.core.registry.generation.addEventHandlers
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
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
        val context =
            BoundedContext(BoundedContextId("orders")) {
                addEventHandlers(
                    StorageEvent::class,
                    listOf(LoadedEventHandler(PrintEventHandler::class)),
                )
            }

        assertTrue(context.handlerLocator.subscribedEventTypes().contains(StorageEvent::class))
    }

    @Test
    @OptIn(GeneratedKBusApi::class)
    fun addEventHandlers_registersOnTheUnderlyingLocatorsIntegrationEventMapper() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        BoundedContext(BoundedContextId("orders"), locator) {
            addEventHandlers(
                StorageEvent::class,
                listOf(LoadedEventHandler(PrintEventHandler::class)),
            )
        }

        assertTrue(locator.subscribedEventTypes().contains(StorageEvent::class))
    }

    @Test
    @OptIn(GeneratedKBusApi::class)
    fun addDomainHandlers_registersOnTheUnderlyingLocatorsDomainEventMapper() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        BoundedContext(BoundedContextId("orders"), locator) {
            addDomainHandlers(
                TestDomainEvent::class,
                listOf(LoadedEventHandler(TestDomainEventHandler::class)),
            )
        }

        assertTrue(locator.subscribedEventTypes().contains(TestDomainEvent::class))
    }

    @Test
    @OptIn(GeneratedKBusApi::class)
    fun addEventHandlers_doesNotRegisterOnAnotherContextsLocator() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val other = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        BoundedContext(BoundedContextId("orders"), locator) {
            addEventHandlers(
                StorageEvent::class,
                listOf(LoadedEventHandler(PrintEventHandler::class)),
            )
        }

        assertFalse(other.subscribedEventTypes().contains(StorageEvent::class))
    }

    @Test
    fun addEventHandlers_registersHandlerClassesWithoutTheGeneratedApiOptIn() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        BoundedContext(BoundedContextId("orders"), locator) {
            addEventHandlers(StorageEvent::class, listOf(PrintEventHandler::class))
        }

        assertTrue(locator.subscribedEventTypes().contains(StorageEvent::class))
    }

    @Test
    fun addDomainHandlers_registersHandlerClassesWithoutTheGeneratedApiOptIn() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        BoundedContext(BoundedContextId("orders"), locator) {
            addDomainHandlers(TestDomainEvent::class, listOf(TestDomainEventHandler::class))
        }

        assertTrue(locator.subscribedEventTypes().contains(TestDomainEvent::class))
    }

    @Test
    fun useInbox_declaresTheInboxTheContextConsumesThrough() {
        val declaredInbox = ContextInbox(InMemoryInboxStore(), InboxAckPolicy.HonourEventStrategy)

        val context = BoundedContext(BoundedContextId("orders")) { useInbox(declaredInbox) }

        assertSame(declaredInbox, context.inbox)
    }

    @Test
    fun constructor_declaresTheInboxTheContextConsumesThrough() {
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
    fun constructor_rejectsAnInboxDeclaredBothAsAnArgumentAndThroughUseInbox() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                BoundedContext(
                    BoundedContextId("orders"),
                    PersistingHandlerLocator(),
                    ContextInbox(InMemoryInboxStore(), InboxAckPolicy.HonourEventStrategy),
                ) {
                    useInbox(ContextInbox(InMemoryInboxStore(), InboxAckPolicy.HonourEventStrategy))
                }
            }

        assertTrue(failure.message!!.contains("orders"))
    }

    @Test
    fun addEventHandlers_registersEveryHandlerClassGiven() {
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

        BoundedContext(BoundedContextId("orders"), locator) {
            addEventHandlers(
                StorageEvent::class,
                listOf(PrintEventHandler::class, OtherPrintEventHandler::class),
            )
        }

        assertEquals(2, locator.handlersFor(StorageEvent("any", mutableListOf())).size)
    }
}
