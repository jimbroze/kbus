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
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEventHandler
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import com.jimbroze.kbus.core.fixtures.TestUnitOfWork
import com.jimbroze.kbus.domain.DomainEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class EventDispatcherTest {
    @Test
    fun test_it_dispatches_event_to_all_handlers() = runTest {
        val results = mutableListOf<String>()
        val dispatcher = EventDispatcher({ emptyList() }, emptyList())

        dispatcher.dispatchIntegrationEvent(
            StorageEvent("string", results),
            listOf(PrintEventHandler(), OtherPrintEventHandler("string")),
        )

        assertEquals("string", results[0])
        assertEquals("string", results[1])
    }

    @Test
    fun test_it_dispatches_domain_event_immediately_by_default() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers = listOf(TestDomainEventHandler(results) as EventHandler<DomainEvent>)
        val dispatcher = EventDispatcher({ handlers }, emptyList())

        dispatcher.dispatchDomainEvent(TestDomainEvent("immediate"), unitOfWork)

        assertEquals(1, results.size)
        assertEquals("immediate", results[0])
    }

    @Test
    fun test_it_schedules_domain_event_for_after_primary_work() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(TestDispatchAtEndOfTransactionHandler(results) as EventHandler<DomainEvent>)
        val dispatcher = EventDispatcher({ handlers }, emptyList())

        dispatcher.dispatchDomainEvent(TestDomainEvent("after-primary"), unitOfWork)

        assertEquals(0, results.size)
        assertEquals(1, unitOfWork.secondaryWork.size)
        unitOfWork.secondaryWork[0].invoke()
        assertEquals(1, results.size)
        assertEquals("after-primary", results[0])
    }

    @Test
    fun test_it_schedules_domain_event_for_after_commit() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(TestDispatchAfterTransactionHandler(results) as EventHandler<DomainEvent>)
        val dispatcher = EventDispatcher({ handlers }, emptyList())

        dispatcher.dispatchDomainEvent(TestDomainEvent("after-commit"), unitOfWork)

        assertEquals(0, results.size)
        assertEquals(1, unitOfWork.postCommitWork.size)
        unitOfWork.postCommitWork[0].invoke()
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
                )

            val dispatcher = EventDispatcher({ handlers }, emptyList())

            dispatcher.dispatchDomainEvent(TestDomainEvent("mixed"), unitOfWork)

            assertEquals(1, results.size)
            assertEquals("mixed", results[0])

            assertEquals(1, unitOfWork.secondaryWork.size)
            unitOfWork.secondaryWork[0].invoke()
            assertEquals(2, results.size)
            assertEquals("mixed", results[1])

            assertEquals(1, unitOfWork.postCommitWork.size)
            unitOfWork.postCommitWork[0].invoke()
            assertEquals(3, results.size)
            assertEquals("mixed", results[2])
        }

    @Test
    fun test_domain_events_are_dispatched_asynchronously_by_default() = runTest {
        // Default DomainEventHandler (no dispatch strategy annotation) should run concurrently.
        // If async: the fast handler finishes before the slow one despite being added second.
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                DelayingDomainEventHandler(results, 100, "slow") as EventHandler<DomainEvent>,
                DelayingDomainEventHandler(results, 0, "fast") as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList())

        dispatcher.dispatchDomainEvent(TestDomainEvent("test"), unitOfWork)

        assertEquals(2, results.size)
        assertEquals("fast", results[0])
        assertEquals("slow", results[1])
    }

    @Test
    fun test_DispatchAfterTransaction_domain_events_are_dispatched_asynchronously() = runTest {
        // DispatchAfterTransaction handlers should run concurrently when their deferred work
        // executes.
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                DelayingDispatchAfterTransactionHandler(results, 100, "slow")
                    as EventHandler<DomainEvent>,
                DelayingDispatchAfterTransactionHandler(results, 0, "fast")
                    as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList())

        dispatcher.dispatchDomainEvent(TestDomainEvent("test"), unitOfWork)

        assertEquals(0, results.size)
        assertEquals(2, unitOfWork.postCommitWork.size)

        // Execute all post-commit work together (simulating the UoW running them)
        unitOfWork.postCommitWork.forEach { it.invoke() }

        assertEquals(2, results.size)
        assertEquals("fast", results[0])
        assertEquals("slow", results[1])
    }

    @Test
    fun test_DispatchAtEndOfTransaction_domain_events_are_dispatched_synchronously() = runTest {
        // DispatchAtEndOfTransaction handlers should run sequentially (in order).
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                DelayingDispatchAtEndOfTransactionHandler(results, 100, "slow")
                    as EventHandler<DomainEvent>,
                DelayingDispatchAtEndOfTransactionHandler(results, 0, "fast")
                    as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList())

        dispatcher.dispatchDomainEvent(TestDomainEvent("test"), unitOfWork)

        assertEquals(0, results.size)
        assertEquals(2, unitOfWork.secondaryWork.size)

        // Execute all secondary work together
        unitOfWork.secondaryWork.forEach { it.invoke() }

        // Synchronous: order preserved regardless of delay
        assertEquals(2, results.size)
        assertEquals("slow", results[0])
        assertEquals("fast", results[1])
    }

    @Test
    fun test_DispatchImmediately_domain_events_are_dispatched_synchronously() = runTest {
        // DispatchImmediately handlers should run sequentially (in order).
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                DelayingDispatchImmediatelyHandler(results, 100, "slow")
                    as EventHandler<DomainEvent>,
                DelayingDispatchImmediatelyHandler(results, 0, "fast") as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList())

        dispatcher.dispatchDomainEvent(TestDomainEvent("test"), unitOfWork)

        // Synchronous: order preserved regardless of delay
        assertEquals(2, results.size)
        assertEquals("slow", results[0])
        assertEquals("fast", results[1])
    }

    @Test
    fun test_integration_events_are_always_dispatched_asynchronously() = runTest {
        // Integration events dispatched via the non-UoW dispatch method should run concurrently.
        val results = mutableListOf<String>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                DelayingIntegrationEventHandler(results, 100, "slow")
                    as EventHandler<IntegrationEvent>,
                DelayingIntegrationEventHandler(results, 0, "fast")
                    as EventHandler<IntegrationEvent>,
            )
        val dispatcher = EventDispatcher({ emptyList() }, emptyList())

        dispatcher.dispatchIntegrationEvent(TestIntegrationEvent("test"), handlers)

        assertEquals(2, results.size)
        assertEquals("fast", results[0])
        assertEquals("slow", results[1])
    }
}
