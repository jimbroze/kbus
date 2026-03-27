package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.DelayingDispatchAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingDispatchAsynchronouslyHandler
import com.jimbroze.kbus.core.fixtures.DelayingDispatchAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingDispatchImmediatelyHandler
import com.jimbroze.kbus.core.fixtures.DelayingDispatchSynchronouslyHandler
import com.jimbroze.kbus.core.fixtures.DelayingDomainEventHandler
import com.jimbroze.kbus.core.fixtures.DelayingIntegrationEventHandler
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
import com.jimbroze.kbus.domain.DomainEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
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
    fun test_DispatchSynchronously_domain_event_handlers_are_dispatched_synchronously() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                DelayingDispatchSynchronouslyHandler(results, 100, "dispatched first, with delay")
                    as EventHandler<DomainEvent>,
                DelayingDispatchSynchronouslyHandler(results, 0, "dispatched second, no delay")
                    as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        dispatcher.dispatchDomainEvent(TestDomainEvent("test"), unitOfWork)

        assertEquals(0, results.size)
        assertEquals(2, unitOfWork.postCommitWork.size)

        unitOfWork.postCommitWork.forEach { it.invoke() }

        // Synchronous: order preserved regardless of delay
        assertEquals(2, results.size)
        assertEquals("dispatched first, with delay", results[0])
        assertEquals("dispatched second, no delay", results[1])
    }

    @Test
    fun test_DispatchAsynchronously_domain_event_handlers_are_dispatched_asynchronously() =
        runTest {
            val results = mutableListOf<String>()
            val unitOfWork = TestUnitOfWork<Any?>()
            @Suppress("UNCHECKED_CAST")
            val handlers =
                listOf(
                    DelayingDispatchAsynchronouslyHandler(
                        results,
                        100,
                        "dispatched first, with delay",
                    )
                        as EventHandler<DomainEvent>,
                    DelayingDispatchAsynchronouslyHandler(results, 0, "dispatched second, no delay")
                        as EventHandler<DomainEvent>,
                )
            val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

            dispatcher.dispatchDomainEvent(TestDomainEvent("test"), unitOfWork)

            assertEquals(0, results.size)
            assertEquals(2, unitOfWork.postCommitWork.size)

            unitOfWork.postCommitWork.forEach { it.invoke() }
            advanceUntilIdle()

            // Async: shorter delay completes first regardless of dispatch order
            assertEquals(2, results.size)
            assertEquals("dispatched second, no delay", results[0])
            assertEquals("dispatched first, with delay", results[1])
        }

    // --- Error handling strategy tests ---

    @Test
    fun test_domain_events_default_to_fire_and_forget_for_async_handlers() = runTest {
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
    fun test_domain_events_default_to_fire_and_forget_for_sync_handlers() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                ThrowingDispatchImmediatelyHandler(results) as EventHandler<DomainEvent>,
                TestDispatchImmediatelyHandler(results) as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        // Plain DomainEvent — fire and forget is the default even for sync handlers
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

    // --- Error strategy + dispatch strategy combination tests ---

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

    // FIXME combo not allowed
    //    @Test
    //    fun test_FailFast_with_DispatchAfterTransaction_throws_first_exception() = runTest {

    // TODO AfterTransaction should always be fire and forget?
    // TODO DispatchAsynchronously should always be fire and forget?

    // TODO change async to concurrent. Make orthogonal to timing & error handling

    // ContinueAndAggregate + deferred dispatch

    @Test
    fun test_ContinueAndAggregate_with_DispatchAtEndOfTransaction_runs_all_then_throws_aggregate() =
        runTest {
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

            // MultipleException thrown when secondary work executes
            val exception =
                assertFailsWith<MultipleException> {
                    unitOfWork.secondaryWork.forEach { it.invoke() }
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

    // FIXME combo not allowed
    //    @Test
    // test_ContinueAndAggregate_with_DispatchAfterTransaction_runs_all_then_throws_aggregate() =

    // Mixed dispatch strategies within same error strategy

    @Test
    fun test_FailFast_with_mixed_dispatch_strategies_throws_for_immediate_handler() = runTest {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        @Suppress("UNCHECKED_CAST")
        val handlers =
            listOf(
                SucceedingFailFastHandler(results) as EventHandler<DomainEvent>,
                ThrowingFailFastAtEndOfTransactionHandler(results) as EventHandler<DomainEvent>,
                ThrowingFailFastAfterTransactionHandler(results) as EventHandler<DomainEvent>,
                ThrowingFailFastHandler(results) as EventHandler<DomainEvent>,
            )
        val dispatcher = EventDispatcher({ handlers }, emptyList(), dispatcherScope = this)

        // Handler 4 (DispatchImmediately, throwing) causes fail-fast exception
        assertFailsWith<TestHandlerException> {
            dispatcher.dispatchDomainEvent(TestFailFastEvent("test"), unitOfWork)
        }

        // Handlers 1 and 4 executed immediately
        assertEquals(2, results.size)
        assertEquals("success:test", results[0])
        assertEquals("threw:test", results[1])

        // Handlers 2 and 3 were scheduled before handler 4 threw
        assertEquals(1, unitOfWork.secondaryWork.size)
        assertEquals(1, unitOfWork.postCommitWork.size)

        // FailFast also applies within deferred work
        assertFailsWith<TestHandlerException> { unitOfWork.secondaryWork.forEach { it.invoke() } }
        assertFailsWith<TestHandlerException> { unitOfWork.postCommitWork.forEach { it.invoke() } }
    }

    @Test
    fun test_ContinueAndAggregate_with_mixed_dispatch_strategies_aggregates_per_dispatch_group() =
        runTest {
            val results = mutableListOf<String>()
            val unitOfWork = TestUnitOfWork<Any?>()
            @Suppress("UNCHECKED_CAST")
            val handlers =
                listOf(
                    ThrowingContinueAndAggregateHandler(results, "immediate-first")
                        as EventHandler<DomainEvent>,
                    ThrowingContinueAndAggregateAtEndOfTransactionHandler(results, "deferred")
                        as EventHandler<DomainEvent>,
                    ThrowingContinueAndAggregateAfterTransactionHandler(results, "async")
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

            assertEquals(2, results.size)
            assertEquals("threw:immediate-first", results[0])
            assertEquals("threw:immediate-second", results[1])

            // Deferred handlers also aggregate when their work runs
            val deferredSecondaryException =
                assertFailsWith<MultipleException> {
                    unitOfWork.secondaryWork.forEach { it.invoke() }
                }
            assertEquals(1, deferredSecondaryException.exceptions.size)
            assertTrue(deferredSecondaryException.exceptions[0].message!!.contains("deferred"))

            val deferredPostCommitException =
                assertFailsWith<MultipleException> {
                    unitOfWork.postCommitWork.forEach { it.invoke() }
                }
            assertEquals(1, deferredPostCommitException.exceptions.size)
            assertTrue(deferredPostCommitException.exceptions[0].message!!.contains("async"))
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
