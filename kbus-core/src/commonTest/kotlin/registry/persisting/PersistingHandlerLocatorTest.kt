package com.jimbroze.kbus.core.registry.persisting

import com.jimbroze.kbus.core.fixtures.OtherPrintEventHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageCommand
import com.jimbroze.kbus.core.fixtures.StorageCommandHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEventHandler
import com.jimbroze.kbus.core.fixtures.TestDomainEventPublisher
import com.jimbroze.kbus.core.fixtures.testCommandDependencies
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.registry.EventHandlerMapping
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PersistingHandlerLocatorTest {
    @Test
    fun test_it_returns_null_if_handler_not_registered() {
        val locator = PersistingHandlerLocator()
        val command = StorageCommand("test", mutableListOf())

        val initialHandler =
            locator.handlerFor(command, CommandDependencies(TestDomainEventPublisher()))
        assertEquals(null, initialHandler)
    }

    @Test
    fun test_locator_can_find_and_create_registered_command_handler() {
        val command = StorageCommand("test", mutableListOf())
        val stores = HandlerFactoryStoreCollection()

        val factory = PersistingHandlerLocator(stores)

        //  TODO test for CommandDependencies passed into handler
        stores.commandStore.registerHandlers(
            StorageCommand::class,
            listOf(CommandHandlerFactory(StorageCommandHandler::class) { StorageCommandHandler() }),
        )

        val registeredHandler = factory.handlerFor(command, testCommandDependencies<Any?>())
        assertIs<StorageCommandHandler>(registeredHandler)
    }

    @Test
    fun test_event_handlers_are_not_found_if_mappings_are_not_registered() {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)

        val eventType = StorageEvent::class
        val event = StorageEvent("test", mutableListOf())

        assertEquals(0, locator.handlersFor(event).size)

        stores.eventStore.registerHandlers(
            eventType,
            listOf(
                EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                EventHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("Still testing the bus")
                },
            ),
        )

        val handlers = locator.handlersFor(event)
        assertEquals(0, handlers.size)
    }

    @Test
    fun test_locator_can_find_registered_event_handlers_when_mappings_are_registered() {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)

        val domainEvent = TestDomainEvent("test")
        val integrationEvent = StorageEvent("test", mutableListOf())

        assertEquals(0, locator.handlersFor(integrationEvent).size)

        stores.eventStore.registerHandlers(
            TestDomainEvent::class,
            listOf(
                EventHandlerFactory(TestDomainEventHandler::class) {
                    TestDomainEventHandler(mutableListOf())
                }
            ),
        )
        stores.eventStore.registerHandlers(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                EventHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("Still testing the bus")
                },
            ),
        )

        locator.domainEventMapper.addDomainHandlers(
            listOf(
                EventHandlerMapping(TestDomainEvent::class, listOf(TestDomainEventHandler::class))
            )
        )
        locator.integrationEventMapper.addEventHandlers(
            listOf(EventHandlerMapping(StorageEvent::class, listOf(PrintEventHandler::class)))
        )

        assertEquals(1, locator.handlersFor(domainEvent).size)
        assertEquals(1, locator.handlersFor(integrationEvent).size)
    }

    @Test
    fun test_locator_throws_exception_if_event_handler_is_mapped_but_not_registered() {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)

        val eventType = StorageEvent::class
        val event = StorageEvent("test", mutableListOf())

        assertEquals(0, locator.handlersFor(event).size)

        locator.integrationEventMapper.addEventHandlers(
            listOf(
                EventHandlerMapping(
                    eventType,
                    listOf(PrintEventHandler::class, OtherPrintEventHandler::class),
                )
            )
        )

        assertFailsWith<IllegalStateException> { locator.handlersFor(event) }
    }
}
