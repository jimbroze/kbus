package com.jimbroze.kbus.core.registry.persisting.store

import com.jimbroze.kbus.contracts.messages.command.TooManyHandlersException
import com.jimbroze.kbus.core.fixtures.OtherPrintEventHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.ReturnCommand
import com.jimbroze.kbus.core.fixtures.ReturnCommandHandler
import com.jimbroze.kbus.core.fixtures.StorageCommand
import com.jimbroze.kbus.core.fixtures.StorageCommandHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class MessageHandlerFactoryStoreTest {

    @Test
    fun `reports no message types when nothing is registered`() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()

        assertEquals(emptySet(), store.registeredTypes())
    }

    @Test
    fun `reports a message type once a handler for it is registered`() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()

        store.registerHandlers(
            StorageEvent::class,
            listOf(EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() }),
        )

        assertEquals(setOf(StorageEvent::class), store.registeredTypes())
    }

    @Test
    fun `finds no handlers for a message with none registered`() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()

        val handlers = store.getHandlers(StorageEvent::class)

        assertEquals(0, handlers.size)
    }

    @Test
    fun `finds the handlers registered for a message`() {
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
    fun `keeps the handlers already registered for a message when more are added`() {
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
    fun `refuses a handler class already registered for a message`() {
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
    fun `refuses a handler class repeated within one registration`() {
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
    fun `removes only the handler classes it is asked to remove`() {
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
    fun `removes every handler for a message when asked for no class in particular`() {
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

        assertEquals(emptySet(), store.registeredTypes())
    }

    @Test
    fun `stops reporting a message type once its last handler is removed`() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()

        store.registerHandlers(
            StorageEvent::class,
            listOf(EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() }),
        )

        store.removeHandlers(StorageEvent::class, listOf(PrintEventHandler::class))

        assertEquals(emptySet(), store.registeredTypes())
    }

    @Test
    fun `ignores a removal for a message it holds no handler for`() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()

        store.removeHandlers(StorageEvent::class, listOf(PrintEventHandler::class))

        assertEquals(emptySet(), store.registeredTypes())
    }

    @Test
    fun `finds the factories for the handler classes it is asked for`() {
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
    fun `finds no factories for handler classes it does not hold`() {
        val store = MessageHandlerFactoryStore<com.jimbroze.kbus.contracts.messages.event.Event>()

        val factories =
            store.getHandlersByType<StorageEvent, PrintEventHandler>(PrintEventHandler::class)

        assertEquals(0, factories.size)
    }

    @Test
    fun `keeps the handlers of different message types apart`() {
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

        assertEquals(setOf(ReturnCommand::class), store.registeredTypes())
    }
}
