package core

import Command
import CommandHandler
import Event
import EventHandler
import MessageStore
import MissingHandlerException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ReturnCommand(val messageData: String) : Command()

class ReturnCommandHandler : CommandHandler<ReturnCommand, Any> {
    override suspend fun handle(message: ReturnCommand): Any {
        return message.messageData
    }
}

open class PrintCommand(val messageData: String) : Command()

class PrintCommandHandler : CommandHandler<PrintCommand, Unit> {
    override suspend fun handle(message: PrintCommand) {
        println(message.messageData)
    }
}

class AnyCommandHandler : CommandHandler<Command, Unit> {
    override suspend fun handle(message: Command) {
    }
}

open class PrintEvent(val eventData: String) : Event()

class PrintEventHandler : EventHandler<PrintEvent> {
    override suspend fun handle(message: PrintEvent) {
        println(message.eventData)
    }
}

class OtherPrintEventHandler : EventHandler<PrintEvent> {
    override suspend fun handle(message: PrintEvent) {
        println(message.eventData)
    }
}

class TestMessageStore {
    @Test
    fun test_handle_handles_a_specific_message() {
        val bus = MessageStore<Command>()

        runBlocking {
            bus.handle(ReturnCommand("Testing"), listOf(ReturnCommandHandler()))
        }
    }

    @Test
    fun test_handle_can_return_a_value() {
        val bus = MessageStore<Command>()

        runBlocking {
            val result = bus.handle(ReturnCommand("Testing"), listOf(ReturnCommandHandler()))

            assertEquals("Testing", result)
        }
    }

    @Test
    fun test_handle_finds_a_previously_registered_message() {
        val bus = MessageStore<Command>()
        bus.registerHandlers(ReturnCommand::class, listOf(ReturnCommandHandler()))

        runBlocking {
            val result = bus.handle(ReturnCommand("Testing"))

            assertEquals("Testing", result)
        }
    }

    @Test
    fun test_is_registered_returns_false_for_non_registered_message() {
        val bus = MessageStore<Command>()

        bus.registerHandlers(PrintCommand::class, listOf(PrintCommandHandler()))

        assert(!bus.isRegistered(ReturnCommand::class))
    }

    @Test
    fun test_isRegistered_returns_true_for_registered_message() {
        val bus = MessageStore<Command>()

        bus.registerHandlers(ReturnCommand::class, listOf(ReturnCommandHandler()))

        assert(bus.isRegistered(ReturnCommand::class))
    }

    @Test
    fun test_getHandlers_returns_registered_handlers() {
        val bus = MessageStore<Event>()

        val handler1 = PrintEventHandler()
        val handler2 = OtherPrintEventHandler()
        bus.registerHandlers(PrintEvent::class, listOf(handler1, handler2))

        assertContains(bus.getHandlers(PrintEvent::class), handler1)
    }

    @Test
    fun test_removeHandlers_removes_handlers_for_a_given_message() {
        val bus = MessageStore<Event>()
        val handler1 = PrintEventHandler()
        val handler2 = OtherPrintEventHandler()
        val handler3 = PrintEventHandler()
        bus.registerHandlers(PrintEvent::class, listOf(handler1, handler2, handler3))

        bus.removeHandlers(PrintEvent::class, listOf(handler1, handler3))

        assertEquals(bus.getHandlers(PrintEvent::class), listOf(handler2))
    }

    @Test
    fun test_removeHandlers_removes_all_handlers_for_a_message_by_default() {
        val bus = MessageStore<Event>()
        bus.registerHandlers(PrintEvent::class, listOf(PrintEventHandler(), OtherPrintEventHandler()))

        bus.removeHandlers(PrintEvent::class)

        assert(!bus.isRegistered(PrintEvent::class))
    }

    @Test
    fun test_removeHandlers_throws_exception_if_message_is_not_registered() {
        val bus = MessageStore<Command>()

        assertThrows<MissingHandlerException> {
            bus.removeHandlers(ReturnCommand::class)
        }
    }
}
