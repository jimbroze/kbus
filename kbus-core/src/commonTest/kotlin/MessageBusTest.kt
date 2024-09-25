package com.jimbroze.kbus.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

open class FailureCommand : Command()

class GenericFailureCommandHandler : CommandHandler<FailureCommand, String, FailureReason> {
    override suspend fun handle(message: FailureCommand): BusResult<String, FailureReason> {
        return failure("The command failed")
    }
}

class BrokenStateFailure(message: String?) : FailureReason(message)

class BrokenStateFailureCommandHandler :
    CommandHandler<FailureCommand, String, BrokenStateFailure> {
    override suspend fun handle(message: FailureCommand): BusResult<String, BrokenStateFailure> {
        return failure(BrokenStateFailure("Illegal state in command handling"))
    }
}

class MultipleFailureCommandHandler : CommandHandler<FailureCommand, String, FailureReason> {
    override suspend fun handle(message: FailureCommand): BusResult<String, FailureReason> {
        return failure(
            listOf(
                GenericFailure("The command failed"),
                BrokenStateFailure("Illegal state in command handling"),
            )
        )
    }
}

open class StorageQuery(val index: Int, val listStore: MutableList<String>) : Query()

class StorageQueryHandler : QueryHandler<StorageQuery, String, GenericFailure> {
    override suspend fun handle(message: StorageQuery): BusResult<String, GenericFailure> {
        return success(message.listStore[message.index])
    }
}

open class FailureQuery : Query()

class FailureQueryHandler : QueryHandler<FailureQuery, String, GenericFailure> {
    override suspend fun handle(message: FailureQuery): BusResult<String, GenericFailure> {
        return failure("The query failed")
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
    fun test_resultFailure_exception_in_command_returns_failure() = runTest {
        val bus = MessageBus()

        val result = bus.execute(FailureCommand(), GenericFailureCommandHandler())

        assertTrue(result.isFailure)
        val failure = result.failureReasonOrNull()
        assertIs<FailureReason>(failure)
        assertEquals("The command failed", failure.message)
        assertEquals("Failure(The command failed)", result.toString())
    }

    @Test
    fun test_failure_will_return_exception_if_provided() = runTest {
        val bus = MessageBus()

        val result = bus.execute(FailureCommand(), BrokenStateFailureCommandHandler())

        assertTrue(result.isFailure)
        val failure = result.failureReasonOrNull()
        assertIs<BrokenStateFailure>(failure)
        assertEquals("Illegal state in command handling", failure.message)
        assertEquals("Failure(Illegal state in command handling)", result.toString())
    }

    @Test
    fun test_failure_can_hold_multiple_exceptions() = runTest {
        val bus = MessageBus()

        val result = bus.execute(FailureCommand(), MultipleFailureCommandHandler())

        assertTrue(result.isFailure)

        assertIs<BusResult<Any?, MultipleFailureReasons>>(result)
        val failureReasons = result.failureReasonOrNull()!!.reasons

        assertEquals(2, failureReasons.size)
        assertIs<GenericFailure>(failureReasons[0])
        assertEquals("The command failed", failureReasons[0].message)
        assertIs<BrokenStateFailure>(failureReasons[1])
        assertEquals("Illegal state in command handling", failureReasons[1].message)
        assertEquals("Failure(There were multiple failures)", result.toString())
    }

    @Test
    fun test_executed_query_returns_a_successful_result_value() = runTest {
        val bus = MessageBus()
        val list = mutableListOf("Test the bus")

        val result = bus.execute(StorageQuery(0, list), StorageQueryHandler())

        assertTrue(result.isSuccess)
        assertEquals("Test the bus", result.getOrNull())
    }

    @Test
    fun test_resultFailure_exception_in_query_returns_failure() = runTest {
        val bus = MessageBus()

        val result = bus.execute(FailureQuery(), FailureQueryHandler())

        assertTrue(result.isFailure)
        val failure = result.failureReasonOrNull()
        assertIs<FailureReason>(failure)
        assertEquals("The query failed", failure.message)
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
            listOf(PrintEventHandler(), OtherPrintEventHandler()),
        )

        assertEquals(2, list.count())
        assertEquals("Test the bus", list[0])
        assertEquals("Test the bus", list[0])
    }

    //    @Test
    //    fun test_command_registers_with_the_command_bus() {
    //        val bus = MessageBus()
    //        assertFalse { bus.isRegistered(StorageCommand::class) }
    //
    //        bus.register(StorageCommand::class, StorageCommandHandler())
    //
    //        assertTrue { bus.isRegistered(StorageCommand::class) }
    //    }

    //    @Test
    //    fun test_execute_executes_a_previously_registered_command() = runTest {
    //        val bus = MessageBus()
    //        bus.register(ReturnCommand::class, ReturnCommandHandler())
    //
    //        val result = bus.execute(ReturnCommand("Test the bus"))
    //
    //        assertEquals(result, "Test the bus")
    //    }

    @Test
    fun test_events_can_register_multiple_handlers() {
        val bus = MessageBus()
        assertEquals(0, bus.hasHandlers(StorageEvent::class))

        bus.register(StorageEvent::class, listOf(PrintEventHandler(), OtherPrintEventHandler()))

        assertEquals(2, bus.hasHandlers(StorageEvent::class))
    }

    @Test
    fun test_dispatch_dispatches_previously_registered_event() = runTest {
        val bus = MessageBus()
        val list = mutableListOf<String>()
        bus.register(StorageEvent::class, listOf(PrintEventHandler()))

        bus.dispatch(StorageEvent("Test the bus", list))

        assertEquals(1, list.count())
        assertEquals("Test the bus", list[0])
    }

    @Test
    fun test_dispatch_combines_registered_and_passed_event_handlers() = runTest {
        val bus = MessageBus()
        val list = mutableListOf<String>()
        bus.register(StorageEvent::class, listOf(PrintEventHandler()))

        bus.dispatch(StorageEvent("Test the bus", list), listOf(OtherPrintEventHandler()))

        assertEquals(2, list.count())
        assertEquals("Test the bus", list[0])
        assertEquals("Test the bus", list[0])
    }

    //    @Test
    //    fun test_command_deregisters_with_the_command_bus() {
    //        val bus = MessageBus()
    //
    //        bus.register(StorageCommand::class, StorageCommandHandler())
    //        bus.deregister(StorageCommand::class)
    //
    //        assertFalse { bus.isRegistered(StorageCommand::class) }
    //    }

    @Test
    fun test_bus_can_deregister_multiple_events_at_once() {
        val bus = MessageBus()
        val handler1 = PrintEventHandler()
        val handler2 = PrintEventHandler()
        val handler3 = PrintEventHandler()
        bus.register(StorageEvent::class, listOf(handler1, handler2, handler3))
        assertEquals(3, bus.hasHandlers(StorageEvent::class))

        bus.deregister(StorageEvent::class, listOf(handler2, handler3))

        assertEquals(1, bus.hasHandlers(StorageEvent::class))
    }

    @Test
    fun test_bus_deregisters_all_events_by_default() {
        val bus = MessageBus()
        bus.register(StorageEvent::class, listOf(PrintEventHandler(), OtherPrintEventHandler()))
        assertEquals(2, bus.hasHandlers(StorageEvent::class))

        bus.deregister(StorageEvent::class)

        assertEquals(0, bus.hasHandlers(StorageEvent::class))
    }

    //    @Test
    //    fun test_is_registered_returns_false_for_command_not_registered() {
    //        val bus = MessageBus()
    //
    //        assertFalse { bus.isRegistered(ReturnCommand::class) }
    //    }

    @Test
    fun test_has_handlers_returns_zero_for_event_not_registered() {
        val bus = MessageBus()

        assertEquals(0, bus.hasHandlers(StorageEvent::class))
    }
}
