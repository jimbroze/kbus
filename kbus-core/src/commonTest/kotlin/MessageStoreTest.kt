package com.jimbroze.kbus.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ReturnCommand(val messageData: String) : Command<String, MessageFailure>()

class ReturnCommandHandler : CommandHandler<ReturnCommand, String, MessageFailure>() {
    override suspend fun handle(message: ReturnCommand): BusResult<String, MessageFailure> {
        return success(message.messageData)
    }
}

open class StorageCommand(val messageData: String, val listStore: MutableList<String>) :
    Command<Unit, MessageFailure>()

class StorageCommandHandler : CommandHandler<StorageCommand, Unit, MessageFailure>() {
    override suspend fun handle(message: StorageCommand): BusResult<Unit, MessageFailure> {
        message.listStore.add(message.messageData)
        return success()
    }
}

class AnyCommandHandler : CommandHandler<Command<Unit, MessageFailure>, Unit, MessageFailure>() {
    override suspend fun handle(
        message: Command<Unit, MessageFailure>
    ): BusResult<Unit, MessageFailure> {
        return success()
    }
}

open class StorageEvent(val eventData: String, val listStore: MutableList<String>) : Event()

class PrintEventHandler : EventHandler<StorageEvent> {
    override suspend fun handle(message: StorageEvent) {
        message.listStore.add(message.eventData)
    }
}

class OtherPrintEventHandler(val toPrint: String) : EventHandler<StorageEvent> {
    override suspend fun handle(message: StorageEvent) {
        message.listStore.add(toPrint)
    }
}

class TestMessageStore {
    @Test
    fun test_handle_handles_a_specific_message() = runTest {
        val bus = MessageStore<Command<*, *>>()

        bus.handle(ReturnCommand("Testing"), listOf(ReturnCommandHandler()))
    }

    @Test
    fun test_handle_can_return_a_value() = runTest {
        val bus = MessageStore<Command<*, *>>()

        val result = bus.handle(ReturnCommand("Testing"), listOf(ReturnCommandHandler()))

        assertIs<BusResult<Any?, MessageFailure>>(result)
        assertEquals("Testing", result.getOrNull())
    }

    @Test
    fun test_handle_finds_a_previously_registered_message() = runTest {
        val bus = MessageStore<Command<*, *>>()
        bus.registerHandlers(ReturnCommand::class, listOf(ReturnCommandHandler()))

        val result = bus.handle(ReturnCommand("Testing"))

        assertIs<BusResult<Any?, MessageFailure>>(result)
        assertEquals("Testing", result.getOrNull())
    }

    @Test
    fun test_is_registered_returns_false_for_non_registered_message() {
        val bus = MessageStore<Command<*, *>>()

        bus.registerHandlers(StorageCommand::class, listOf(StorageCommandHandler()))

        assertTrue(!bus.isRegistered(ReturnCommand::class))
    }

    @Test
    fun test_isRegistered_returns_true_for_registered_message() {
        val bus = MessageStore<Command<*, *>>()

        bus.registerHandlers(ReturnCommand::class, listOf(ReturnCommandHandler()))

        assertTrue(bus.isRegistered(ReturnCommand::class))
    }

    @Test
    fun test_getHandlers_returns_registered_handlers() {
        val bus = MessageStore<Event>()

        val handler1 = PrintEventHandler()
        val handler2 = OtherPrintEventHandler("Still testing the bus")
        bus.registerHandlers(StorageEvent::class, listOf(handler1, handler2))

        assertContains(bus.getHandlers(StorageEvent::class), handler1)
    }

    @Test
    fun test_removeHandlers_removes_handlers_for_a_given_message() {
        val bus = MessageStore<Event>()
        val handler1 = PrintEventHandler()
        val handler2 = OtherPrintEventHandler("Still testing the bus")
        val handler3 = PrintEventHandler()
        bus.registerHandlers(StorageEvent::class, listOf(handler1, handler2, handler3))

        bus.removeHandlers(StorageEvent::class, listOf(handler1, handler3))

        assertEquals(bus.getHandlers(StorageEvent::class), listOf(handler2))
    }

    @Test
    fun test_removeHandlers_removes_all_handlers_for_a_message_by_default() {
        val bus = MessageStore<Event>()
        bus.registerHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler(), OtherPrintEventHandler("Still testing the bus")),
        )

        bus.removeHandlers(StorageEvent::class)

        assertFalse(bus.isRegistered(StorageEvent::class))
    }

    @Test
    fun test_removeHandlers_throws_exception_if_message_is_not_registered() {
        val bus = MessageStore<Command<*, *>>()

        assertFailsWith<MissingHandlerException> { bus.removeHandlers(ReturnCommand::class) }
    }
}
