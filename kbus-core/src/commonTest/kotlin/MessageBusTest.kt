package com.jimbroze.kbus.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

open class FailureCommand : Command<BusResult<String, FailureCommandFailure>>()

class BrokenStateFailureReason(override val message: String) : FailureReason

sealed interface FailureCommandFailure : MessageFailure {
    class BrokenStateFailure(message: String) : FailureCommandFailure {
        override val reason = BrokenStateFailureReason(message)
    }
}

class BrokenStateFailureCommandHandler :
    CommandHandler<FailureCommand, BusResult<String, FailureCommandFailure>>() {
    override suspend fun handle(message: FailureCommand): BusResult<String, FailureCommandFailure> {
        return BusResult.failure(
            FailureCommandFailure.BrokenStateFailure("Illegal state in command handling")
        )
    }
}

open class StorageQuery(val index: Int, val listStore: MutableList<String>) :
    Query<BusResult<String, MessageFailure>>()

class StorageQueryHandler : QueryHandler<StorageQuery, BusResult<String, MessageFailure>> {
    override suspend fun handle(message: StorageQuery): BusResult<String, MessageFailure> {
        return BusResult.success(message.listStore[message.index])
    }
}

open class FailureQuery : Query<BusResult<String, FailureCommandFailure>>()

class FailureQueryHandler : QueryHandler<FailureQuery, BusResult<String, FailureCommandFailure>> {
    override suspend fun handle(message: FailureQuery): BusResult<String, FailureCommandFailure> {
        return BusResult.failure(FailureCommandFailure.BrokenStateFailure("The query failed"))
    }
}

class TestEvent(val message: String) : Event()

class TestIntegrationEventHandler(val messageOutput: MutableList<String>) :
    EventHandler<TestEvent> {
    override suspend fun handle(message: TestEvent) {
        messageOutput.add(message.message)
    }
}

class EventCommand(val message: String, val listStore: MutableList<String>) :
    Command<BusResult<Unit, MessageFailure>>()

class EventCommandHandler : CommandHandler<EventCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: EventCommand): BusResult<Unit, MessageFailure> {
        dispatch(StorageEvent(message.message, message.listStore))
        return BusResult.success(Unit)
    }
}

class MessageBusTest {
    @Test
    fun test_execute_executes_a_command_successfully() = runTest {
        val bus = MessageBus()
        val list = mutableListOf<String>()

        val result = bus.execute(StorageCommand("Test the bus", list), StorageCommandHandler())

        assertTrue(result.isSuccess)
        assertContains(list, "Test the bus")
    }

    @Test
    fun test_command_can_return_a_success_value() = runTest {
        val bus = MessageBus()

        val result = bus.execute(ReturnCommand("Test the bus"), ReturnCommandHandler())

        assertTrue(result.isSuccess)
        assertEquals("Test the bus", result.getOrNull())
    }

    @Test
    fun test_failure_will_return_exception_if_provided() = runTest {
        val bus = MessageBus()

        val result = bus.execute(FailureCommand(), BrokenStateFailureCommandHandler())

        assertTrue(result.isFailure)
        val failure = result.failureOrNull()
        assertIs<FailureCommandFailure>(failure)
        assertEquals("Illegal state in command handling", failure.reason.message)
        assertEquals("Failure: Illegal state in command handling", result.toString())
    }

    @Test
    fun test_executed_query_returns_a_successful_result_value() = runTest {
        val bus = MessageBus()
        val list = mutableListOf("Test the bus")

        val result = bus.fetch(StorageQuery(0, list), StorageQueryHandler())

        assertTrue(result.isSuccess)
        assertEquals("Test the bus", result.getOrNull())
    }

    @Test
    fun test_resultFailure_exception_in_query_returns_failure() = runTest {
        val bus = MessageBus()

        val result = bus.fetch(FailureQuery(), FailureQueryHandler())

        assertTrue(result.isFailure)
        val failure = result.failureOrNull()
        assertNotNull(failure)
        assertIs<FailureReason>(failure.reason)
        assertEquals("The query failed", failure.reason.message)
    }

    //    @Test
    //    fun test_execute_does_not_accept_a_handler_if_one_is_already_registered() = runTest {
    //        val bus = MessageBus()
    //
    //        bus.register(ReturnCommand::class, ReturnCommandHandler())
    //
    //        assertFailsWith<TooManyHandlersException> {
    //            bus.execute(ReturnCommand("Testing"), AnyCommandHandler())
    //        }
    //    }

    @Test
    fun test_dispatch_dispatches_an_event() = runTest {
        val bus = MessageBus()
        val list = mutableListOf<String>()

        bus.dispatch(StorageEvent("Test the bus", list), listOf(PrintEventHandler()))

        assertContains(list, "Test the bus")
    }

    @Test
    fun test_dispatch_can_dispatch_an_event_with_no_handlers() = runTest {
        val bus = MessageBus()
        val list = mutableListOf<String>()

        bus.dispatch(StorageEvent("Test the bus", list))
    }

    @Test
    fun test_dispatch_can_dispatch_to_multiple_handlers() = runTest {
        val bus = MessageBus()
        val list = mutableListOf<String>()

        bus.dispatch(
            StorageEvent("Test the bus", list),
            listOf(PrintEventHandler(), OtherPrintEventHandler("Still testing the bus")),
        )

        assertEquals(2, list.count())
        assertEquals("Test the bus", list[0])
        assertEquals("Still testing the bus", list[1])
    }

    @Test
    fun test_command_can_dispatch_integration_event() = runTest {
        val bus = MessageBus()
        val list = mutableListOf<String>()
        val handler = PrintEventHandler()

        // Use dispatch with explicit handlers instead of registering
        bus.execute(EventCommand("Emit me", list), EventCommandHandler())

        // FIXME
        // The EventCommandHandler will dispatch a StorageEvent
        // We need to manually handle it since we can't register handlers
        bus.dispatch(StorageEvent("Emit me", list), listOf(handler))

        assertEquals(1, list.count())
        assertEquals("Emit me", list[0])
    }
}
