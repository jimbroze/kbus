package com.jimbroze.kbus.core

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PersistingHandlerLocatorTest {
    @Test
    fun test_it_returns_null_if_handler_not_registered() {
        val locator = PersistingHandlerLocator()
        val command = StorageCommand("test", mutableListOf())

        val initialHandler = locator.messageMapper.handlerFor(command)
        assertEquals(null, initialHandler)
    }

    @Test
    fun test_locator_can_find_registered_command_handler() {
        val locator = PersistingHandlerLocator()
        val commandType = StorageCommand::class
        val command = StorageCommand("test", mutableListOf())

        (locator.messageMapper as PersistingHandlerMapper).register(
            commandType,
            TestMessageHandlerFactory(StorageCommandHandler::class) { StorageCommandHandler() },
        )

        val registeredHandler = locator.messageMapper.handlerFor(command)
        assertIs<StorageCommandHandler>(registeredHandler)
    }

    @Test
    fun test_locator_can_create_command_handler() {
        val handlerType =
            StorageCommandHandler::class as KClass<CommandHandler<StorageCommand, *, *>>
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)

        stores.commandStore.registerHandlers(
            StorageCommand::class,
            listOf(
                TestMessageHandlerFactory(StorageCommandHandler::class) { StorageCommandHandler() }
            ),
        )

        val handler = locator.factory.create(handlerType)
        assertIs<StorageCommandHandler>(handler)
    }

    @Test
    fun test_locator_can_find_registered_event_handlers() {
        val locator = PersistingHandlerLocator()
        val eventType = StorageEvent::class
        val event = StorageEvent("test", mutableListOf())

        assertEquals(0, locator.messageMapper.handlersFor(event).size)

        locator.eventManager.register(
            eventType,
            listOf(
                TestMessageHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                TestMessageHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("Still testing the bus")
                },
            ),
        )

        val handlers = locator.messageMapper.handlersFor(event)
        assertEquals(2, handlers.size)
    }

    @Test
    fun test_locator_can_deregister_event_handlers() {
        val locator = PersistingHandlerLocator()
        val eventType = StorageEvent::class
        val event = StorageEvent("test", mutableListOf())

        locator.eventManager.register(
            eventType,
            listOf(
                TestMessageHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                TestMessageHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("Still testing the bus")
                },
            ),
        )

        assertEquals(2, locator.messageMapper.handlersFor(event).size)

        locator.eventManager.deregister(eventType, listOf(PrintEventHandler::class))

        assertEquals(1, locator.messageMapper.handlersFor(event).size)
    }
}
