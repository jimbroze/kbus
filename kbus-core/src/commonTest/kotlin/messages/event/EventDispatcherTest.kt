package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.DefaultPhaseFailFastHandler
import com.jimbroze.kbus.core.fixtures.DelayingDispatchAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingDispatchAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingDispatchImmediatelyHandler
import com.jimbroze.kbus.core.fixtures.DelayingDomainEventHandler
import com.jimbroze.kbus.core.fixtures.DelayingIntegrationEventHandler
import com.jimbroze.kbus.core.fixtures.DelayingSequentialAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingSequentialDomainEventHandler
import com.jimbroze.kbus.core.fixtures.DelayingSequentialEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingSequentialImmediateHandler
import com.jimbroze.kbus.core.fixtures.OtherPrintEventHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.SucceedingContinueAndAggregateAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.SucceedingContinueAndAggregateHandler
import com.jimbroze.kbus.core.fixtures.SucceedingFailFastAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.SucceedingFailFastHandler
import com.jimbroze.kbus.core.fixtures.SucceedingFireAndForgetAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.SucceedingFireAndForgetAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.SucceedingFireAndForgetHandler
import com.jimbroze.kbus.core.fixtures.SucceedingSequentialContinueAndAggregateHandler
import com.jimbroze.kbus.core.fixtures.SucceedingSequentialFailFastHandler
import com.jimbroze.kbus.core.fixtures.SucceedingSequentialFireAndForgetHandler
import com.jimbroze.kbus.core.fixtures.TestContinueAndAggregateEvent
import com.jimbroze.kbus.core.fixtures.TestDispatchAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.TestDispatchAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.TestDispatchImmediatelyHandler
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEventHandler
import com.jimbroze.kbus.core.fixtures.TestFailFastEvent
import com.jimbroze.kbus.core.fixtures.TestFireAndForgetEvent
import com.jimbroze.kbus.core.fixtures.TestHandlerException
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import com.jimbroze.kbus.core.fixtures.TestSequentialContinueAndAggregateEvent
import com.jimbroze.kbus.core.fixtures.TestSequentialDomainEvent
import com.jimbroze.kbus.core.fixtures.TestSequentialFailFastEvent
import com.jimbroze.kbus.core.fixtures.TestSequentialFireAndForgetEvent
import com.jimbroze.kbus.core.fixtures.TestUnitOfWork
import com.jimbroze.kbus.core.fixtures.ThrowingContinueAndAggregateAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.ThrowingContinueAndAggregateAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.ThrowingContinueAndAggregateHandler
import com.jimbroze.kbus.core.fixtures.ThrowingDispatchImmediatelyHandler
import com.jimbroze.kbus.core.fixtures.ThrowingDomainEventHandler
import com.jimbroze.kbus.core.fixtures.ThrowingFailFastAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.ThrowingFailFastAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.ThrowingFailFastHandler
import com.jimbroze.kbus.core.fixtures.ThrowingFireAndForgetAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.ThrowingFireAndForgetAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.ThrowingFireAndForgetHandler
import com.jimbroze.kbus.core.fixtures.ThrowingSequentialContinueAndAggregateHandler
import com.jimbroze.kbus.core.fixtures.ThrowingSequentialFailFastHandler
import com.jimbroze.kbus.core.fixtures.ThrowingSequentialFireAndForgetHandler
import com.jimbroze.kbus.domain.DomainEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@Suppress("LargeClass")
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

    // --- Dispatch phase tests ---

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
    fun test_it_handles_multiple_domain_event_handlers_with_different_dispatch_phases() = runTest {
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

        // All handlers executed regardless of dispatch phase
        assertEquals(4, results.size)
        assertEquals(listOf("mixed", "mixed", "mixed", "mixed"), results)
    }

    // --- Concurrency tests ---

    @Test
    fun test_domain_events_are_dispatched_concurrently_by_default() = runTest {
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

        // Concurrent: shorter delay completes first regardless of dispatch order
        assertEquals(2, results.size)
        assertEquals("dispatched second, no delay", results[0])
        assertEquals("dispatched first, with delay", results[1])
    }

    @Test
    fun test_sequential_events_are_dispatched_sequentially() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                DelayingSequentialDomainEventHandler(results, 100, "dispatched first, with delay")
                    as EventHandler<DomainEvent>,
                DelayingSequentialDomainEventHandler(results, 0, "dispatched second, no delay")
                    as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestSequentialDomainEvent("test"), unitOfWork)
        unitOfWork.executeAllScheduledWork()
        advanceUntilIdle()

        // Sequential: order preserved regardless of delay
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

    // --- Concurrency × Dispatch phase orthogonality ---
    // Concurrency is determined by the event, not the dispatch phase.

    @Test
    fun test_concurrent_event_dispatches_immediate_handlers_concurrently() = runTest {
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

        // Concurrent: shorter delay completes first regardless of dispatch order
        assertEquals(2, results.size)
        assertEquals("dispatched second, no delay", results[0])
        assertEquals("dispatched first, with delay", results[1])
    }

    @Test
    fun test_sequential_event_dispatches_immediate_handlers_sequentially() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                DelayingSequentialImmediateHandler(results, 100, "dispatched first, with delay")
                    as EventHandler<DomainEvent>,
                DelayingSequentialImmediateHandler(results, 0, "dispatched second, no delay")
                    as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestSequentialDomainEvent("test"), unitOfWork)

        // Sequential: order preserved regardless of delay
        assertEquals(2, results.size)
        assertEquals("dispatched first, with delay", results[0])
        assertEquals("dispatched second, no delay", results[1])
    }

    @Test
    fun test_concurrent_event_dispatches_end_of_transaction_handlers_concurrently() = runTest {
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
                DelayingDispatchAtEndOfTransactionHandler(results, 0, "dispatched second, no delay")
                    as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestDomainEvent("test"), unitOfWork)

        assertEquals(0, results.size)

        unitOfWork.secondaryWork.forEach { it.invoke() }
        advanceUntilIdle()

        // Concurrent: shorter delay completes first
        assertEquals(2, results.size)
        assertEquals("dispatched second, no delay", results[0])
        assertEquals("dispatched first, with delay", results[1])
    }

    @Test
    fun test_sequential_event_dispatches_end_of_transaction_handlers_sequentially() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                DelayingSequentialEndOfTransactionHandler(
                    results,
                    100,
                    "dispatched first, with delay",
                )
                    as EventHandler<DomainEvent>,
                DelayingSequentialEndOfTransactionHandler(results, 0, "dispatched second, no delay")
                    as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestSequentialDomainEvent("test"), unitOfWork)

        assertEquals(0, results.size)

        unitOfWork.secondaryWork.forEach { it.invoke() }

        // Sequential: order preserved regardless of delay
        assertEquals(2, results.size)
        assertEquals("dispatched first, with delay", results[0])
        assertEquals("dispatched second, no delay", results[1])
    }

    @Test
    fun test_concurrent_event_dispatches_after_transaction_handlers_concurrently() = runTest {
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
                DelayingDispatchAfterTransactionHandler(results, 0, "dispatched second, no delay")
                    as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestDomainEvent("test"), unitOfWork)

        assertEquals(0, results.size)

        unitOfWork.postCommitWork.forEach { it.invoke() }
        advanceUntilIdle()

        // Concurrent: shorter delay completes first
        assertEquals(2, results.size)
        assertEquals("dispatched second, no delay", results[0])
        assertEquals("dispatched first, with delay", results[1])
    }

    @Test
    fun test_sequential_event_dispatches_after_transaction_handlers_sequentially() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                DelayingSequentialAfterTransactionHandler(
                    results,
                    100,
                    "dispatched first, with delay",
                )
                    as EventHandler<DomainEvent>,
                DelayingSequentialAfterTransactionHandler(results, 0, "dispatched second, no delay")
                    as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestSequentialDomainEvent("test"), unitOfWork)

        assertEquals(0, results.size)

        unitOfWork.postCommitWork.forEach { it.invoke() }

        // Sequential: order preserved regardless of delay
        assertEquals(2, results.size)
        assertEquals("dispatched first, with delay", results[0])
        assertEquals("dispatched second, no delay", results[1])
    }

    // --- Error handling strategy tests ---

    @Test
    fun test_domain_events_default_to_fire_and_forget_for_default_phase_handlers() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                ThrowingDomainEventHandler(results) as EventHandler<DomainEvent>,
                TestDomainEventHandler(results) as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        // Plain DomainEvent — fire and forget is the default
        dispatcher.dispatchDomainEvent(TestDomainEvent("test"), unitOfWork)
        unitOfWork.executeAllScheduledWork()
        advanceUntilIdle()

        // Both handlers were invoked (throwing handler ran but didn't stop the second)
        assertEquals(2, results.size)
        assertEquals("threw:test", results[0])
        assertEquals("test", results[1])
    }

    @Test
    fun test_domain_events_default_to_fire_and_forget_for_immediate_handlers() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                ThrowingDispatchImmediatelyHandler(results) as EventHandler<DomainEvent>,
                TestDispatchImmediatelyHandler(results) as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        // Plain DomainEvent — fire and forget is the default even for immediate handlers
        dispatcher.dispatchDomainEvent(TestDomainEvent("test"), unitOfWork)

        // Both handlers executed
        assertEquals(2, results.size)
        assertEquals("threw:test", results[0])
        assertEquals("test", results[1])
    }

    @Test
    fun test_FireAndForgetDomainEvent_does_not_propagate_exceptions() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                ThrowingFireAndForgetHandler(results) as EventHandler<DomainEvent>,
                SucceedingFireAndForgetHandler(results) as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestFireAndForgetEvent("test"), unitOfWork)

        // Both handlers executed despite the first one throwing
        assertEquals(2, results.size)
        assertEquals("threw:test", results[0])
        assertEquals("success:test", results[1])
    }

    @Test
    fun test_FailFast_throws_first_exception_immediately() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                ThrowingFailFastHandler(results) as EventHandler<DomainEvent>,
                SucceedingFailFastHandler(results) as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        val exception =
            assertFailsWith<TestHandlerException> {
                dispatcher.dispatchDomainEvent(TestFailFastEvent("test"), unitOfWork)
            }

        assertEquals("FailFast handler failed for: test", exception.message)
        // Second handler should NOT have executed
        assertEquals(1, results.size)
        assertEquals("threw:test", results[0])
    }

    @Test
    fun test_FailFast_does_not_throw_when_all_handlers_succeed() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                SucceedingFailFastHandler(results) as EventHandler<DomainEvent>,
                SucceedingFailFastHandler(results) as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestFailFastEvent("test"), unitOfWork)

        assertEquals(2, results.size)
    }

    @Test
    fun test_ContinueAndAggregate_runs_all_handlers_then_throws_aggregate() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                ThrowingContinueAndAggregateHandler(results, "first") as EventHandler<DomainEvent>,
                SucceedingContinueAndAggregateHandler(results, "second")
                    as EventHandler<DomainEvent>,
                ThrowingContinueAndAggregateHandler(results, "third") as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        val exception =
            assertFailsWith<MultipleException> {
                dispatcher.dispatchDomainEvent(TestContinueAndAggregateEvent("test"), unitOfWork)
            }

        // All three handlers executed
        assertEquals(3, results.size)
        assertEquals("threw:first", results[0])
        assertEquals("success:second", results[1])
        assertEquals("threw:third", results[2])

        // AggregateException contains the two failures
        assertEquals(2, exception.exceptions.size)
        assertTrue(exception.exceptions[0].message!!.contains("first"))
        assertTrue(exception.exceptions[1].message!!.contains("third"))
    }

    @Test
    fun test_ContinueAndAggregate_does_not_throw_when_all_handlers_succeed() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                SucceedingContinueAndAggregateHandler(results, "first")
                    as EventHandler<DomainEvent>,
                SucceedingContinueAndAggregateHandler(results, "second")
                    as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestContinueAndAggregateEvent("test"), unitOfWork)

        assertEquals(2, results.size)
    }

    // --- Error strategy + dispatch phase combination tests ---

    // FireAndForget + deferred dispatch

    @Test
    fun test_FireAndForget_with_DispatchAtEndOfTransaction_does_not_propagate_exceptions() =
        runTest {
            val results = mutableListOf<String>()
            val unitOfWork = TestUnitOfWork<Any?>()
            @Suppress("UNCHECKED_CAST")
            val handlers =
                listOf(
                    ThrowingFireAndForgetAtEndOfTransactionHandler(results)
                        as EventHandler<DomainEvent>,
                    SucceedingFireAndForgetAtEndOfTransactionHandler(results)
                        as EventHandler<DomainEvent>,
                )
            val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

            dispatcher.dispatchDomainEvent(TestFireAndForgetEvent("test"), unitOfWork)

            assertEquals(0, results.size)

            unitOfWork.secondaryWork.forEach { it.invoke() }

            assertEquals(2, results.size)
            assertEquals("threw:test", results[0])
            assertEquals("success:test", results[1])
        }

    @Test
    fun test_FireAndForget_with_DispatchAfterTransaction_does_not_propagate_exceptions() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                ThrowingFireAndForgetAfterTransactionHandler(results) as EventHandler<DomainEvent>,
                SucceedingFireAndForgetAfterTransactionHandler(results) as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestFireAndForgetEvent("test"), unitOfWork)

        assertEquals(0, results.size)

        unitOfWork.postCommitWork.forEach { it.invoke() }
        advanceUntilIdle()

        assertEquals(2, results.size)
        assertEquals("threw:test", results[0])
        assertEquals("success:test", results[1])
    }

    // FailFast + deferred dispatch

    @Test
    fun test_FailFast_with_DispatchAtEndOfTransaction_throws_first_exception() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                ThrowingFailFastAtEndOfTransactionHandler(results) as EventHandler<DomainEvent>,
                SucceedingFailFastAtEndOfTransactionHandler(results) as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        // Dispatch itself does not throw — handlers are deferred
        dispatcher.dispatchDomainEvent(TestFailFastEvent("test"), unitOfWork)
        assertEquals(0, results.size)

        // FailFast throws on first failure when secondary work executes
        assertFailsWith<TestHandlerException> { unitOfWork.secondaryWork.forEach { it.invoke() } }

        // Second handler should NOT have executed
        assertEquals(1, results.size)
        assertEquals("threw:test", results[0])
    }

    @Test
    fun test_FailFast_with_DispatchAtEndOfTransaction_does_not_throw_when_all_succeed() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                SucceedingFailFastAtEndOfTransactionHandler(results) as EventHandler<DomainEvent>,
                SucceedingFailFastAtEndOfTransactionHandler(results) as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestFailFastEvent("test"), unitOfWork)
        unitOfWork.secondaryWork.forEach { it.invoke() }

        assertEquals(2, results.size)
    }

    // ContinueAndAggregate + deferred dispatch

    @Test
    fun test_ContinueAndAggregate_with_DispatchAtEndOfTransaction_runs_all_handlers() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                ThrowingContinueAndAggregateAtEndOfTransactionHandler(results, "first")
                    as EventHandler<DomainEvent>,
                SucceedingContinueAndAggregateAtEndOfTransactionHandler(results, "second")
                    as EventHandler<DomainEvent>,
                ThrowingContinueAndAggregateAtEndOfTransactionHandler(results, "third")
                    as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        // Dispatch itself does not throw — handlers are deferred
        dispatcher.dispatchDomainEvent(TestContinueAndAggregateEvent("test"), unitOfWork)
        assertEquals(0, results.size)

        // ContinueAndAggregate catches exceptions so deferred work completes without throwing
        // NOTE: aggregated exceptions are not currently re-thrown for deferred handlers
        unitOfWork.secondaryWork.forEach { it.invoke() }

        // All three handlers executed despite failures
        assertEquals(3, results.size)
        assertEquals("threw:first", results[0])
        assertEquals("success:second", results[1])
        assertEquals("threw:third", results[2])
    }

    // Mixed dispatch phases within same error strategy

    @Test
    fun test_FailFast_with_mixed_dispatch_phases_throws_for_immediate_handler() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                // SECONDARY listed first so it is grouped and scheduled before IMMEDIATE executes
                ThrowingFailFastAtEndOfTransactionHandler(results) as EventHandler<DomainEvent>,
                SucceedingFailFastHandler(results) as EventHandler<DomainEvent>,
                ThrowingFailFastHandler(results) as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        // IMMEDIATE group (handlers 2 and 3) causes fail-fast exception
        assertFailsWith<TestHandlerException> {
            dispatcher.dispatchDomainEvent(TestFailFastEvent("test"), unitOfWork)
        }

        // Only IMMEDIATE handlers executed
        assertEquals(2, results.size)
        assertEquals("success:test", results[0])
        assertEquals("threw:test", results[1])

        // SECONDARY handler was scheduled (its group was processed before IMMEDIATE)
        assertEquals(1, unitOfWork.secondaryWork.size)

        // FailFast also applies within deferred work
        assertFailsWith<TestHandlerException> { unitOfWork.secondaryWork.forEach { it.invoke() } }
    }

    @Test
    fun test_ContinueAndAggregate_with_mixed_dispatch_phases_aggregates_per_dispatch_group() =
        runTest {
            val results = mutableListOf<String>()
            val unitOfWork = TestUnitOfWork<Any?>()
            @Suppress("UNCHECKED_CAST")
            val handlers =
                listOf(
                    // SECONDARY listed first so it is grouped and scheduled before IMMEDIATE
                    ThrowingContinueAndAggregateAtEndOfTransactionHandler(results, "deferred")
                        as EventHandler<DomainEvent>,
                    ThrowingContinueAndAggregateHandler(results, "immediate-first")
                        as EventHandler<DomainEvent>,
                    ThrowingContinueAndAggregateHandler(results, "immediate-second")
                        as EventHandler<DomainEvent>,
                )
            val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

            // Immediate handlers aggregate their exceptions
            val immediateException =
                assertFailsWith<MultipleException> {
                    dispatcher.dispatchDomainEvent(
                        TestContinueAndAggregateEvent("test"),
                        unitOfWork,
                    )
                }

            assertEquals(2, immediateException.exceptions.size)
            assertTrue(immediateException.exceptions[0].message!!.contains("immediate-first"))
            assertTrue(immediateException.exceptions[1].message!!.contains("immediate-second"))

            // Only IMMEDIATE handlers executed so far
            assertEquals(2, results.size)
            assertEquals("threw:immediate-first", results[0])
            assertEquals("threw:immediate-second", results[1])

            // SECONDARY handler was scheduled (its group was processed before IMMEDIATE)
            assertEquals(1, unitOfWork.secondaryWork.size)
        }

    // --- Dispatch phase validation tests ---
    // FailFast and ContinueAndAggregate cannot be dispatched POST_COMMIT (outside the unit of work)

    @Test
    fun test_FailFast_with_DispatchAfterTransaction_handler_throws_validation_error() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(ThrowingFailFastAfterTransactionHandler(results) as EventHandler<DomainEvent>)
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        val exception =
            assertFailsWith<IllegalStateException> {
                dispatcher.dispatchDomainEvent(TestFailFastEvent("test"), unitOfWork)
            }

        assertTrue(exception.message!!.contains("fail-fast or aggregate error strategies"))
    }

    @Test
    fun test_ContinueAndAggregate_with_DispatchAfterTransaction_handler_throws_validation_error() =
        runTest {
            val results = mutableListOf<String>()
            val unitOfWork = TestUnitOfWork<Any?>()
            @Suppress("UNCHECKED_CAST")
            val handlers =
                listOf(
                    ThrowingContinueAndAggregateAfterTransactionHandler(results, "post-commit")
                        as EventHandler<DomainEvent>
                )
            val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

            val exception =
                assertFailsWith<IllegalStateException> {
                    dispatcher.dispatchDomainEvent(
                        TestContinueAndAggregateEvent("test"),
                        unitOfWork,
                    )
                }

            assertTrue(exception.message!!.contains("fail-fast or aggregate error strategies"))
        }

    @Test
    fun test_FailFast_with_default_phase_handler_throws_validation_error() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers = listOf(DefaultPhaseFailFastHandler(results) as EventHandler<DomainEvent>)
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        assertFailsWith<IllegalStateException> {
            dispatcher.dispatchDomainEvent(TestFailFastEvent("test"), unitOfWork)
        }
    }

    @Test
    fun test_FireAndForget_with_DispatchAfterTransaction_is_allowed() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                SucceedingFireAndForgetAfterTransactionHandler(results) as EventHandler<DomainEvent>
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestFireAndForgetEvent("test"), unitOfWork)

        assertEquals(1, unitOfWork.postCommitWork.size)
        unitOfWork.postCommitWork[0].invoke()
        advanceUntilIdle()
        assertEquals(1, results.size)
        assertEquals("success:test", results[0])
    }

    // --- Concurrency × Error strategy orthogonality ---
    // Error strategy behavior is the same regardless of sequential/concurrent dispatch.

    @Test
    fun test_sequential_FailFast_throws_first_exception_and_stops() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                ThrowingSequentialFailFastHandler(results) as EventHandler<DomainEvent>,
                SucceedingSequentialFailFastHandler(results) as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        val exception =
            assertFailsWith<TestHandlerException> {
                dispatcher.dispatchDomainEvent(TestSequentialFailFastEvent("test"), unitOfWork)
            }

        assertEquals("FailFast handler failed for: test", exception.message)
        assertEquals(1, results.size)
        assertEquals("threw:test", results[0])
    }

    @Test
    fun test_sequential_FireAndForget_does_not_propagate_exceptions() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                ThrowingSequentialFireAndForgetHandler(results) as EventHandler<DomainEvent>,
                SucceedingSequentialFireAndForgetHandler(results) as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestSequentialFireAndForgetEvent("test"), unitOfWork)

        // Both handlers executed despite the first one throwing
        assertEquals(2, results.size)
        assertEquals("threw:test", results[0])
        assertEquals("success:test", results[1])
    }

    @Test
    fun test_sequential_ContinueAndAggregate_runs_all_handlers_then_throws_aggregate() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                ThrowingSequentialContinueAndAggregateHandler(results, "first")
                    as EventHandler<DomainEvent>,
                SucceedingSequentialContinueAndAggregateHandler(results, "second")
                    as EventHandler<DomainEvent>,
                ThrowingSequentialContinueAndAggregateHandler(results, "third")
                    as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        val exception =
            assertFailsWith<MultipleException> {
                dispatcher.dispatchDomainEvent(
                    TestSequentialContinueAndAggregateEvent("test"),
                    unitOfWork,
                )
            }

        // All three handlers executed
        assertEquals(3, results.size)
        assertEquals("threw:first", results[0])
        assertEquals("success:second", results[1])
        assertEquals("threw:third", results[2])

        assertEquals(2, exception.exceptions.size)
        assertTrue(exception.exceptions[0].message!!.contains("first"))
        assertTrue(exception.exceptions[1].message!!.contains("third"))
    }

    // --- Integration event tests ---

    @Test
    fun test_integration_event_dispatch_is_fire_and_forget() = runTest {
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
