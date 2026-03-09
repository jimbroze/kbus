package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.contracts.result.FailureReason
import com.jimbroze.kbus.core.fixtures.BrokenStateFailureCommandHandler
import com.jimbroze.kbus.core.fixtures.FailureCommand
import com.jimbroze.kbus.core.fixtures.FailureCommandFailure
import com.jimbroze.kbus.core.fixtures.FailureQuery
import com.jimbroze.kbus.core.fixtures.FailureQueryHandler
import com.jimbroze.kbus.core.fixtures.ReturnCommand
import com.jimbroze.kbus.core.fixtures.ReturnCommandHandler
import com.jimbroze.kbus.core.fixtures.StorageCommand
import com.jimbroze.kbus.core.fixtures.StorageCommandHandler
import com.jimbroze.kbus.core.fixtures.StorageQuery
import com.jimbroze.kbus.core.fixtures.StorageQueryHandler
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.persisting.store.QueryHandlerFactory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MessageBusTest {
    @Test
    fun test_execute_executes_a_command_successfully() = runTest {
        val stores = HandlerFactoryStoreCollection()
        stores.commandStore.registerHandlers(
            StorageCommand::class,
            listOf(CommandHandlerFactory(StorageCommandHandler::class) { StorageCommandHandler() }),
        )

        val bus = MessageBus(PersistingHandlerLocator(stores))
        val list = mutableListOf<String>()

        val result = bus.execute(StorageCommand("Test the bus", list))

        assertTrue(result.isSuccess)
        assertContains(list, "Test the bus")
    }

    @Test
    fun test_command_can_return_a_success_value() = runTest {
        val stores = HandlerFactoryStoreCollection()
        stores.commandStore.registerHandlers(
            ReturnCommand::class,
            listOf(CommandHandlerFactory(ReturnCommandHandler::class) { ReturnCommandHandler() }),
        )

        val bus = MessageBus(PersistingHandlerLocator(stores))

        val result = bus.execute(ReturnCommand("Test the bus"))

        assertTrue(result.isSuccess)
        assertEquals("Test the bus", result.getOrNull())
    }

    @Test
    fun test_failure_will_return_exception_if_provided() = runTest {
        val stores = HandlerFactoryStoreCollection()
        stores.commandStore.registerHandlers(
            FailureCommand::class,
            listOf(
                CommandHandlerFactory(BrokenStateFailureCommandHandler::class) {
                    BrokenStateFailureCommandHandler()
                }
            ),
        )

        val bus = MessageBus(PersistingHandlerLocator(stores))

        val result = bus.execute(FailureCommand())

        assertTrue(result.isFailure)
        val failure = result.failureOrNull()
        assertIs<FailureCommandFailure>(failure)
        assertEquals("Illegal state in command handling", failure.reason.message)
        assertEquals("Failure: Illegal state in command handling", result.toString())
    }

    @Test
    fun test_executed_query_returns_a_successful_result_value() = runTest {
        val stores = HandlerFactoryStoreCollection()
        stores.queryStore.registerHandlers(
            StorageQuery::class,
            listOf(QueryHandlerFactory(StorageQueryHandler::class) { StorageQueryHandler() }),
        )

        val bus = MessageBus(PersistingHandlerLocator(stores))
        val list = mutableListOf("Test the bus")

        val result = bus.fetch(StorageQuery(0, list))

        assertTrue(result.isSuccess)
        assertEquals("Test the bus", result.getOrNull())
    }

    @Test
    fun test_resultFailure_exception_in_query_returns_failure() = runTest {
        val stores = HandlerFactoryStoreCollection()
        stores.queryStore.registerHandlers(
            FailureQuery::class,
            listOf(QueryHandlerFactory(FailureQueryHandler::class) { FailureQueryHandler() }),
        )

        val bus = MessageBus(PersistingHandlerLocator(stores))

        val result = bus.fetch(FailureQuery())

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

    //    @Test
    //    fun test_dispatch_dispatches_an_event() = runTest {
    //        val bus = MessageBus()
    //        val list = mutableListOf<String>()
    //
    //        bus.dispatch(StorageEvent("Test the bus", list), listOf(PrintEventHandler()))
    //
    //        assertContains(list, "Test the bus")
    //    }

    //    @Test
    //    fun test_dispatch_can_dispatch_an_event_with_no_handlers() = runTest {
    //        val bus = MessageBus()
    //        val list = mutableListOf<String>()
    //
    //        bus.dispatch(StorageEvent("Test the bus", list))
    //    }

    //    @Test
    //    fun test_dispatch_can_dispatch_to_multiple_handlers() = runTest {
    //        val bus = MessageBus()
    //        val list = mutableListOf<String>()
    //
    //        bus.dispatch(
    //            StorageEvent("Test the bus", list),
    //            listOf(PrintEventHandler(), OtherPrintEventHandler("Still testing the bus")),
    //        )
    //
    //        assertEquals(2, list.count())
    //        assertEquals("Test the bus", list[0])
    //        assertEquals("Still testing the bus", list[1])
    //    }

    //    @Test
    //    fun test_command_can_dispatch_integration_event() = runTest {
    //        val bus = MessageBus()
    //        val list = mutableListOf<String>()
    //        val handler = PrintEventHandler()
    //
    //        // Use dispatch with explicit handlers instead of registering
    //        bus.execute(EventCommand("Emit me", list), EventCommandHandler())
    //
    //        // FIXME
    //        // The EventCommandHandler will dispatch a StorageEvent
    //        // We need to manually handle it since we can't register handlers
    //        bus.dispatch(StorageEvent("Emit me", list), listOf(handler))
    //
    //        assertEquals(1, list.count())
    //        assertEquals("Emit me", list[0])
    //    }
}
