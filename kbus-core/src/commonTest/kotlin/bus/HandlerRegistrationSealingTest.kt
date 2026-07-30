package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.ReturnCommand
import com.jimbroze.kbus.core.fixtures.ReturnCommandHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.StorageQuery
import com.jimbroze.kbus.core.fixtures.StorageQueryHandler
import com.jimbroze.kbus.core.module.BoundedContext
import com.jimbroze.kbus.core.module.BoundedContextId
import com.jimbroze.kbus.core.registry.HandlerRegistrationSealedException
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.persisting.store.QueryHandlerFactory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Commands and queries must be fully registered before a bus is built, because owner lookup is the
 * bus's alone to resolve. Event handlers are deliberately exempt — the generated bus exposes its
 * contexts only once constructed, so registering on a live bus is its documented API.
 */
class HandlerRegistrationSealingTest {
    @Test
    fun registeringACommandHandlerAfterTheBusWasConstructed_throws() {
        val stores = HandlerFactoryStoreCollection()
        MessageBus(PersistingHandlerLocator(stores))

        assertFailsWith<HandlerRegistrationSealedException> {
            stores.commandStore.registerHandlers(
                ReturnCommand::class,
                listOf(
                    CommandHandlerFactory(ReturnCommandHandler::class) { ReturnCommandHandler() }
                ),
            )
        }
    }

    @Test
    fun registeringAQueryHandlerAfterTheBusWasConstructed_throws() {
        val stores = HandlerFactoryStoreCollection()
        MessageBus(PersistingHandlerLocator(stores))

        assertFailsWith<HandlerRegistrationSealedException> {
            stores.queryStore.registerHandlers(
                StorageQuery::class,
                listOf(QueryHandlerFactory(StorageQueryHandler::class) { StorageQueryHandler() }),
            )
        }
    }

    @Test
    fun removingACommandHandlerAfterTheBusWasConstructed_throws() {
        val stores = HandlerFactoryStoreCollection()
        stores.commandStore.registerHandlers(
            ReturnCommand::class,
            listOf(CommandHandlerFactory(ReturnCommandHandler::class) { ReturnCommandHandler() }),
        )
        MessageBus(PersistingHandlerLocator(stores))

        assertFailsWith<HandlerRegistrationSealedException> {
            stores.commandStore.removeHandlers(ReturnCommand::class, null)
        }
    }

    @Test
    fun everyContextIsSealed_notOnlyTheFirst() {
        val firstStores = HandlerFactoryStoreCollection()
        val secondStores = HandlerFactoryStoreCollection()
        MessageBus(
            PersistingHandlerLocator(),
            contexts =
                listOf(
                    BoundedContext(
                        BoundedContextId("first"),
                        PersistingHandlerLocator(firstStores),
                    ),
                    BoundedContext(
                        BoundedContextId("second"),
                        PersistingHandlerLocator(secondStores),
                    ),
                ),
        )

        assertFailsWith<HandlerRegistrationSealedException> {
            secondStores.commandStore.registerHandlers(
                ReturnCommand::class,
                listOf(
                    CommandHandlerFactory(ReturnCommandHandler::class) { ReturnCommandHandler() }
                ),
            )
        }
    }

    @Test
    fun registeringAnEventHandlerAfterTheBusWasConstructed_isStillAllowed() {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val context = BoundedContext(BoundedContextId("orders"), locator)
        MessageBus(PersistingHandlerLocator(), contexts = listOf(context))

        context.addEventHandlers(StorageEvent::class, PrintEventHandler::class)

        assertTrue(locator.hasHandlersFor(StorageEvent("any", mutableListOf())))
    }
}
