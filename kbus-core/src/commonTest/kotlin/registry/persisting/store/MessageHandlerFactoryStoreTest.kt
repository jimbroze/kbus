package com.jimbroze.kbus.core.registry.persisting.store

import com.jimbroze.kbus.contracts.messages.command.TooManyHandlersException
import com.jimbroze.kbus.core.registry.OtherPrintEventHandler
import com.jimbroze.kbus.core.registry.PrintEventHandler
import com.jimbroze.kbus.core.registry.ReturnCommand
import com.jimbroze.kbus.core.registry.ReturnCommandHandler
import com.jimbroze.kbus.core.registry.StorageCommand
import com.jimbroze.kbus.core.registry.StorageCommandHandler
import com.jimbroze.kbus.core.registry.StorageEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MessageHandlerFactoryStoreTest {

    @Test
    fun test_isRegistered_returns_false_when_no_handlers_registered() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()

        assertFalse(store.isRegistered(StorageEvent::class))
    }

    @Test
    fun test_isRegistered_returns_true_after_registering_handlers() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()

        store.registerHandlers(
            StorageEvent::class,
            listOf(EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() }),
        )

        assertTrue(store.isRegistered(StorageEvent::class))
    }

    @Test
    fun test_getHandlers_returns_empty_list_when_no_handlers_registered() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()

        val handlers = store.getHandlers(StorageEvent::class)

        assertEquals(0, handlers.size)
    }

    @Test
    fun test_getHandlers_returns_registered_handlers() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()

        store.registerHandlers(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                EventHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("test")
                },
            ),
        )

        val handlers = store.getHandlers(StorageEvent::class)

        assertEquals(2, handlers.size)
    }

    @Test
    fun test_registerHandlers_appends_to_existing_handlers() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()

        store.registerHandlers(
            StorageEvent::class,
            listOf(EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() }),
        )
        store.registerHandlers(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("test")
                }
            ),
        )

        val handlers = store.getHandlers(StorageEvent::class)

        assertEquals(2, handlers.size)
    }

    @Test
    fun test_registerHandlers_throws_on_duplicate_handler_type() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()

        store.registerHandlers(
            StorageEvent::class,
            listOf(EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() }),
        )

        assertFailsWith<TooManyHandlersException> {
            store.registerHandlers(
                StorageEvent::class,
                listOf(EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() }),
            )
        }
    }

    @Test
    fun test_registerHandlers_throws_on_duplicate_within_same_batch() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()

        store.registerHandlers(
            StorageEvent::class,
            listOf(EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() }),
        )

        assertFailsWith<TooManyHandlersException> {
            store.registerHandlers(
                StorageEvent::class,
                listOf(
                    EventHandlerFactory(OtherPrintEventHandler::class) {
                        OtherPrintEventHandler("a")
                    },
                    EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                ),
            )
        }
    }

    @Test
    fun test_removeHandlers_removes_specific_handler_types() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()

        store.registerHandlers(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                EventHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("test")
                },
            ),
        )

        store.removeHandlers(StorageEvent::class, listOf(PrintEventHandler::class))

        val handlers = store.getHandlers(StorageEvent::class)

        assertEquals(1, handlers.size)
        assertIs<EventHandlerFactory<StorageEvent, OtherPrintEventHandler>>(handlers[0])
    }

    @Test
    fun test_removeHandlers_removes_all_handlers_when_types_is_null() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()

        store.registerHandlers(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                EventHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("test")
                },
            ),
        )

        store.removeHandlers(StorageEvent::class, null)

        assertFalse(store.isRegistered(StorageEvent::class))
    }

    @Test
    fun test_removeHandlers_does_nothing_for_unregistered_message() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()

        store.removeHandlers(StorageEvent::class, listOf(PrintEventHandler::class))

        assertFalse(store.isRegistered(StorageEvent::class))
    }

    @Test
    fun test_getHandlersByType_returns_matching_factories() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()

        store.registerHandlers(
            StorageEvent::class,
            listOf(
                EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() },
                EventHandlerFactory(OtherPrintEventHandler::class) {
                    OtherPrintEventHandler("test")
                },
            ),
        )

        val factories =
            store.getHandlersByType<StorageEvent, PrintEventHandler>(PrintEventHandler::class)

        assertEquals(1, factories.size)
        assertIs<EventHandlerFactory<StorageEvent, PrintEventHandler>>(factories[0])
    }

    @Test
    fun test_getHandlersByType_returns_empty_list_when_no_match() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()

        val factories =
            store.getHandlersByType<StorageEvent, PrintEventHandler>(PrintEventHandler::class)

        assertEquals(0, factories.size)
    }

    @Test
    fun test_different_message_types_are_independent() {
        val store =
            MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.command.Command<*>>()

        store.registerHandlers(
            StorageCommand::class,
            listOf(CommandHandlerFactory(StorageCommandHandler::class) { StorageCommandHandler() }),
        )
        store.registerHandlers(
            ReturnCommand::class,
            listOf(CommandHandlerFactory(ReturnCommandHandler::class) { ReturnCommandHandler() }),
        )

        assertEquals(1, store.getHandlers(StorageCommand::class).size)
        assertEquals(1, store.getHandlers(ReturnCommand::class).size)

        store.removeHandlers(StorageCommand::class, null)

        assertFalse(store.isRegistered(StorageCommand::class))
        assertTrue(store.isRegistered(ReturnCommand::class))
    }
}
