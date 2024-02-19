package com.jimbroze.kbus.core

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ReturnCommand(val messageData: String) : Command()

class ReturnCommandHandler : CommandHandler<ReturnCommand, Any> {
    override suspend fun handle(message: ReturnCommand): Any {
        return message.messageData
    }
}

open class StorageCommand(val messageData: String, val listStore: MutableList<String>) : Command()

class StorageCommandHandler : CommandHandler<StorageCommand, Unit> {
    override suspend fun handle(message: StorageCommand) {
        message.listStore.add(message.messageData)
    }
}

class AnyCommandHandler : CommandHandler<Command, Unit> {
    override suspend fun handle(message: Command) {
    }
}

open class StorageEvent(val eventData: String, val listStore: MutableList<String>) : Event()

class PrintEventHandler : EventHandler<StorageEvent> {
    override suspend fun handle(message: StorageEvent) {
        message.listStore.add(message.eventData)
    }
}

class OtherPrintEventHandler : EventHandler<StorageEvent> {
    override suspend fun handle(message: StorageEvent) {
        message.listStore.add(message.eventData)
    }
}

class TestMessageStore {
    @Test
    fun test_handle_handles_a_specific_message() = runTest {
        val bus = MessageStore<Command>()

        bus.handle(ReturnCommand("Testing"), listOf(ReturnCommandHandler()))
    }

    @Test
    fun test_handle_can_return_a_value() = runTest {
        val bus = MessageStore<Command>()

        val result = bus.handle(ReturnCommand("Testing"), listOf(ReturnCommandHandler()))

        assertEquals("Testing", result)
    }

    @Test
    fun test_handle_finds_a_previously_registered_message() = runTest {
        val bus = MessageStore<Command>()
        bus.registerHandlers(ReturnCommand::class, listOf(ReturnCommandHandler()))

        val result = bus.handle(ReturnCommand("Testing"))

        assertEquals("Testing", result)
    }

    @Test
    fun test_is_registered_returns_false_for_non_registered_message() {
        val bus = MessageStore<Command>()

        bus.registerHandlers(StorageCommand::class, listOf(StorageCommandHandler()))

        assertTrue(!bus.isRegistered(ReturnCommand::class))
    }

    @Test
    fun test_isRegistered_returns_true_for_registered_message() {
        val bus = MessageStore<Command>()

        bus.registerHandlers(ReturnCommand::class, listOf(ReturnCommandHandler()))

        assertTrue(bus.isRegistered(ReturnCommand::class))
    }

    @Test
    fun test_getHandlers_returns_registered_handlers() {
        val bus = MessageStore<Event>()

        val handler1 = PrintEventHandler()
        val handler2 = OtherPrintEventHandler()
        bus.registerHandlers(StorageEvent::class, listOf(handler1, handler2))

        assertContains(bus.getHandlers(StorageEvent::class), handler1)
    }

    @Test
    fun test_removeHandlers_removes_handlers_for_a_given_message() {
        val bus = MessageStore<Event>()
        val handler1 = PrintEventHandler()
        val handler2 = OtherPrintEventHandler()
        val handler3 = PrintEventHandler()
        bus.registerHandlers(StorageEvent::class, listOf(handler1, handler2, handler3))

        bus.removeHandlers(StorageEvent::class, listOf(handler1, handler3))

        assertEquals(bus.getHandlers(StorageEvent::class), listOf(handler2))
    }

    @Test
    fun test_removeHandlers_removes_all_handlers_for_a_message_by_default() {
        val bus = MessageStore<Event>()
        bus.registerHandlers(StorageEvent::class, listOf(PrintEventHandler(), OtherPrintEventHandler()))

        bus.removeHandlers(StorageEvent::class)

        assertFalse(bus.isRegistered(StorageEvent::class))
    }

    @Test
    fun test_removeHandlers_throws_exception_if_message_is_not_registered() {
        val bus = MessageStore<Command>()

        assertFailsWith<MissingHandlerException> {
            bus.removeHandlers(ReturnCommand::class)
        }
    }
}
