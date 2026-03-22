package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.DelayingDispatchAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingDispatchAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingDispatchImmediatelyHandler
import com.jimbroze.kbus.core.fixtures.DelayingDomainEventHandler
import com.jimbroze.kbus.core.fixtures.DelayingIntegrationEventHandler
import com.jimbroze.kbus.core.fixtures.OtherPrintEventHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.TestDispatchAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.TestDispatchAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.TestDispatchImmediatelyHandler
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEventHandler
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import com.jimbroze.kbus.core.fixtures.TestUnitOfWork
import com.jimbroze.kbus.domain.DomainEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EventDispatcherTest {
    @Test
    fun test_it_dispatches_event_to_all_handlers() = runTest {
        val results = mutableListOf<String>()
        val dispatcher = EventDispatcher({ emptyList() }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchIntegrationEvent(
            StorageEvent("string", results),
            listOf(PrintEventHandler(), OtherPrintEventHandler("string")),
        )

        advanceUntilIdle()

        assertEquals(2, results.size)
        assertEquals("string", results[0])
        assertEquals("string", results[1])
    }

    @Test
    fun test_it_dispatches_domain_event_handler_after_transaction_by_default() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers = listOf(TestDomainEventHandler(results) as EventHandler<DomainEvent>)
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestDomainEvent("immediate"), unitOfWork)

        advanceUntilIdle()

        assertEquals(1, unitOfWork.postCommitWork.size)
    }

    @Test
    fun test_it_can_schedule_domain_event_for_after_primary_work() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(TestDispatchAtEndOfTransactionHandler(results) as EventHandler<DomainEvent>)
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestDomainEvent("after-primary"), unitOfWork)

        assertEquals(0, results.size)
        assertEquals(1, unitOfWork.secondaryWork.size)
        unitOfWork.secondaryWork[0].invoke()
        assertEquals(1, results.size)
        assertEquals("after-primary", results[0])
    }

    @Test
    fun test_it_can_schedule_domain_event_for_after_commit() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(TestDispatchAfterTransactionHandler(results) as EventHandler<DomainEvent>)
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestDomainEvent("after-commit"), unitOfWork)

        assertEquals(0, results.size)
        assertEquals(1, unitOfWork.postCommitWork.size)
        unitOfWork.postCommitWork[0].invoke()
        advanceUntilIdle()
        assertEquals(1, results.size)
        assertEquals("after-commit", results[0])
    }

    @Test
    fun test_it_handles_multiple_domain_event_handlers_with_different_dispatch_strategies() =
        runTest {
            val results = mutableListOf<String>()
            val unitOfWork = TestUnitOfWork<Any?>()
            @Suppress("UNCHECKED_CAST")
            val handlers =
                listOf(
                    TestDomainEventHandler(results) as EventHandler<DomainEvent>,
                    TestDispatchAtEndOfTransactionHandler(results) as EventHandler<DomainEvent>,
                    TestDispatchAfterTransactionHandler(results) as EventHandler<DomainEvent>,
                    TestDispatchImmediatelyHandler(results) as EventHandler<DomainEvent>,
                )

            val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

            dispatcher.dispatchDomainEvent(TestDomainEvent("mixed"), unitOfWork)
            unitOfWork.executeAllScheduledWork()
            advanceUntilIdle()

            // All handlers executed regardless of dispatch strategy
            assertEquals(4, results.size)
            assertEquals(listOf("mixed", "mixed", "mixed", "mixed"), results)
        }

    @Test
    fun test_domain_event_handlers_are_dispatched_asynchronously_by_default() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                DelayingDomainEventHandler(results, 100, "dispatched first, with delay")
                    as EventHandler<DomainEvent>,
                DelayingDomainEventHandler(results, 0, "dispatched second, no delay")
                    as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestDomainEvent("test"), unitOfWork)
        unitOfWork.executeAllScheduledWork()
        advanceUntilIdle()

        // Async: shorter delay completes first regardless of dispatch order
        assertEquals(2, results.size)
        assertEquals("dispatched second, no delay", results[0])
        assertEquals("dispatched first, with delay", results[1])
    }

    @Test
    fun test_DispatchAfterTransaction_domain_event_handlers_are_dispatched_asynchronously() =
        runTest {
            val results = mutableListOf<String>()
            val unitOfWork = TestUnitOfWork<Any?>()
            @Suppress("UNCHECKED_CAST")
            val handlers =
                listOf(
                    DelayingDispatchAfterTransactionHandler(
                        results,
                        100,
                        "dispatched first, with delay",
                    )
                        as EventHandler<DomainEvent>,
                    DelayingDispatchAfterTransactionHandler(
                        results,
                        0,
                        "dispatched second, no delay",
                    )
                        as EventHandler<DomainEvent>,
                )
            val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

            dispatcher.dispatchDomainEvent(TestDomainEvent("test"), unitOfWork)

            assertEquals(0, results.size)
            assertEquals(2, unitOfWork.postCommitWork.size)

            unitOfWork.postCommitWork.forEach { it.invoke() }
            advanceUntilIdle()

            assertEquals(2, results.size)
            assertEquals("dispatched second, no delay", results[0])
            assertEquals("dispatched first, with delay", results[1])
        }

    @Test
    fun test_DispatchAtEndOfTransaction_domain_event_handlers_are_dispatched_synchronously() =
        runTest {
            val results = mutableListOf<String>()
            val unitOfWork = TestUnitOfWork<Any?>()
            @Suppress("UNCHECKED_CAST")
            val handlers =
                listOf(
                    DelayingDispatchAtEndOfTransactionHandler(
                        results,
                        100,
                        "dispatched first, with delay",
                    )
                        as EventHandler<DomainEvent>,
                    DelayingDispatchAtEndOfTransactionHandler(
                        results,
                        0,
                        "dispatched second, no delay",
                    )
                        as EventHandler<DomainEvent>,
                )
            val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

            dispatcher.dispatchDomainEvent(TestDomainEvent("test"), unitOfWork)

            assertEquals(0, results.size)
            assertEquals(2, unitOfWork.secondaryWork.size)

            // Synchronous: order preserved regardless of delay
            unitOfWork.secondaryWork[0].invoke()
            assertEquals(1, results.size)
            assertEquals("dispatched first, with delay", results[0])

            unitOfWork.secondaryWork[1].invoke()
            assertEquals(2, results.size)
            assertEquals("dispatched second, no delay", results[1])
        }

    @Test
    fun test_DispatchImmediately_domain_event_handlers_are_dispatched_synchronously() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                DelayingDispatchImmediatelyHandler(results, 100, "dispatched first, with delay")
                    as EventHandler<DomainEvent>,
                DelayingDispatchImmediatelyHandler(results, 0, "dispatched second, no delay")
                    as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestDomainEvent("test"), unitOfWork)

        // Synchronous: order preserved regardless of delay
        assertEquals(2, results.size)
        assertEquals("dispatched first, with delay", results[0])
        assertEquals("dispatched second, no delay", results[1])
    }

    @Test
    fun test_integration_events_are_always_dispatched_asynchronously() = runTest {
        val results = mutableListOf<String>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                DelayingIntegrationEventHandler(results, 100, "dispatched first, with delay")
                    as EventHandler<IntegrationEvent>,
                DelayingIntegrationEventHandler(results, 0, "dispatched second, no delay")
                    as EventHandler<IntegrationEvent>,
            )
        val dispatcher = EventDispatcher({ emptyList() }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchIntegrationEvent(TestIntegrationEvent("test"), handlers)

        advanceUntilIdle()

        assertEquals(2, results.size)
        assertEquals("dispatched second, no delay", results[0])
        assertEquals("dispatched first, with delay", results[1])
    }

    @Test
    fun test_async_dispatch_is_fire_and_forget() = runTest {
        val results = mutableListOf<String>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                DelayingIntegrationEventHandler(results, 100, "delayed handler")
                    as EventHandler<IntegrationEvent>
            )
        val dispatcher = EventDispatcher({ emptyList() }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchIntegrationEvent(TestIntegrationEvent("test"), handlers)

        // Fire-and-forget: dispatch returns before handlers complete
        assertEquals(0, results.size)

        advanceUntilIdle()

        assertEquals(1, results.size)
        assertEquals("delayed handler", results[0])
    }
}
