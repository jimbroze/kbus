package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.CapturingContextMiddleware
import com.jimbroze.kbus.core.fixtures.DefaultPhaseFailFastHandler
import com.jimbroze.kbus.core.fixtures.DelayingDispatchAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingDispatchAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingDispatchImmediatelyHandler
import com.jimbroze.kbus.core.fixtures.DelayingDomainEventHandler
import com.jimbroze.kbus.core.fixtures.DelayingFireAndForgetAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingIntegrationEventHandler
import com.jimbroze.kbus.core.fixtures.DelayingSequentialAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingSequentialDomainEventHandler
import com.jimbroze.kbus.core.fixtures.DelayingSequentialEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingSequentialImmediateHandler
import com.jimbroze.kbus.core.fixtures.EmptyMiddlewareInvocationContext
import com.jimbroze.kbus.core.fixtures.OtherPrintEventHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.RecordingIntegrationEventPublisher
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
import com.jimbroze.kbus.core.middleware.invocationContextOf
import com.jimbroze.kbus.domain.event.DomainEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
class EventDispatcherTest {

    // =========================================================================
    // TEST FIXTURE
    // Abstracts setup, state (results/UoW), and type-erasure casting.
    // =========================================================================
    private class TestEnv(val scope: TestScope) {
        val results = mutableListOf<String>()
        val unitOfWork = TestUnitOfWork<Any?>()
        lateinit var dispatcher: EventDispatcher

        fun withDomainHandlers(vararg handlers: EventHandler<*>): TestEnv {
            @Suppress("UNCHECKED_CAST")
            val castedHandlers = handlers.toList() as List<EventHandler<DomainEvent>>
            dispatcher =
                EventDispatcher(
                    { castedHandlers },
                    emptyList(),
                    dispatcherScope = scope,
                    invocationContextProvider = { EmptyMiddlewareInvocationContext },
                )
            return this
        }

        suspend fun dispatch(event: DomainEvent) = dispatcher.dispatchDomainEvent(event, unitOfWork)

        suspend fun dispatchIntegration(event: IntegrationEvent, vararg handlers: EventHandler<*>) {
            @Suppress("UNCHECKED_CAST")
            val castedHandlers = handlers.toList() as List<EventHandler<IntegrationEvent>>
            dispatcher =
                EventDispatcher(
                    { emptyList() },
                    emptyList(),
                    dispatcherScope = scope,
                    invocationContextProvider = { EmptyMiddlewareInvocationContext },
                )
            dispatcher.dispatchIntegrationEvent(event, castedHandlers)
        }

        suspend fun flushSecondaryWork() = unitOfWork.secondaryWork.forEach { it.invoke() }

        suspend fun flushPostCommitWork() = unitOfWork.postCommitWork.forEach { it.invoke() }

        suspend fun flushAllScheduledWork() = unitOfWork.executeAllScheduledWork()
    }

    // =========================================================================
    // CORE DISPATCHING
    // =========================================================================

    @Test
    fun it_dispatches_event_to_all_handlers() = runTest {
        val env = TestEnv(this)
        env.dispatchIntegration(
            StorageEvent("string", env.results),
            PrintEventHandler(),
            OtherPrintEventHandler("string"),
        )
        advanceUntilIdle()

        assertEquals(listOf("string", "string"), env.results)
    }

    // =========================================================================
    // DISPATCH PHASES
    // =========================================================================

    @Test
    fun it_dispatches_domain_event_handler_after_transaction_by_default() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(TestDomainEventHandler(mutableListOf()))
        env.dispatch(TestDomainEvent("immediate"))
        advanceUntilIdle()

        assertEquals(1, env.unitOfWork.postCommitWork.size)
    }

    @Test
    fun it_schedules_domain_event_for_after_primary_work() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(TestDispatchAtEndOfTransactionHandler(env.results))
        env.dispatch(TestDomainEvent("after-primary"))

        assertEquals(0, env.results.size, "Should not execute immediately")
        env.flushSecondaryWork()
        assertEquals(listOf("after-primary"), env.results)
    }

    @Test
    fun it_schedules_domain_event_for_after_commit() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(TestDispatchAfterTransactionHandler(env.results))
        env.dispatch(TestDomainEvent("after-commit"))

        assertEquals(0, env.results.size)
        env.flushPostCommitWork()
        advanceUntilIdle()
        assertEquals(listOf("after-commit"), env.results)
    }

    @Test
    fun it_handles_multiple_handlers_with_mixed_dispatch_phases_seamlessly() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            TestDomainEventHandler(env.results),
            TestDispatchAtEndOfTransactionHandler(env.results),
            TestDispatchAfterTransactionHandler(env.results),
            TestDispatchImmediatelyHandler(env.results),
        )

        env.dispatch(TestDomainEvent("mixed"))
        env.flushAllScheduledWork()
        advanceUntilIdle()

        assertEquals(listOf("mixed", "mixed", "mixed", "mixed"), env.results)
    }

    // =========================================================================
    // CONCURRENCY VS SEQUENTIAL DISPATCH
    // =========================================================================

    @Test
    fun it_dispatches_domain_events_concurrently() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            DelayingDomainEventHandler(env.results, 100, "first (delayed)"),
            DelayingDomainEventHandler(env.results, 0, "second (fast)"),
        )
        env.dispatch(TestDomainEvent("test"))
        env.flushAllScheduledWork()
        advanceUntilIdle()

        assertEquals(listOf("second (fast)", "first (delayed)"), env.results)
    }

    @Test
    fun it_dispatches_integration_events_asynchronously_as_fire_and_forget() = runTest {
        val env = TestEnv(this)
        env.dispatchIntegration(
            TestIntegrationEvent("test"),
            DelayingIntegrationEventHandler(env.results, 100, "delayed handler"),
        )

        assertEquals(0, env.results.size, "Dispatch returns before handlers complete")
        advanceUntilIdle()
        assertEquals(listOf("delayed handler"), env.results)
    }

    @Test
    fun integration_events_are_dispatched_concurrently() = runTest {
        val env = TestEnv(this)
        env.dispatchIntegration(
            TestIntegrationEvent("test"),
            DelayingIntegrationEventHandler(env.results, 100, "first (delayed)"),
            DelayingIntegrationEventHandler(env.results, 0, "second (fast)"),
        )
        advanceUntilIdle()

        assertEquals(listOf("second (fast)", "first (delayed)"), env.results)
    }

    @Test
    fun it_preserves_order_for_sequential_domain_events_regardless_of_delay() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            DelayingSequentialDomainEventHandler(env.results, 100, "first (delayed)"),
            DelayingSequentialDomainEventHandler(env.results, 0, "second (fast)"),
        )
        env.dispatch(TestSequentialDomainEvent("test"))
        env.flushAllScheduledWork()
        advanceUntilIdle()

        assertEquals(listOf("first (delayed)", "second (fast)"), env.results)
    }

    // =========================================================================
    // ORTHOGONALITY: CONCURRENCY × DISPATCH PHASE
    // =========================================================================

    @Test
    fun concurrent_event_dispatches_immediate_handlers_concurrently() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            DelayingDispatchImmediatelyHandler(env.results, 100, "first"),
            DelayingDispatchImmediatelyHandler(env.results, 0, "second"),
        )
        env.dispatch(TestDomainEvent("test"))
        assertEquals(listOf("second", "first"), env.results)
    }

    @Test
    fun sequential_event_dispatches_immediate_handlers_sequentially() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            DelayingSequentialImmediateHandler(env.results, 100, "first"),
            DelayingSequentialImmediateHandler(env.results, 0, "second"),
        )
        env.dispatch(TestSequentialDomainEvent("test"))
        assertEquals(listOf("first", "second"), env.results)
    }

    @Test
    fun concurrent_event_dispatches_end_of_transaction_handlers_concurrently() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            DelayingDispatchAtEndOfTransactionHandler(env.results, 100, "first"),
            DelayingDispatchAtEndOfTransactionHandler(env.results, 0, "second"),
        )
        env.dispatch(TestDomainEvent("test"))
        env.flushSecondaryWork()
        advanceUntilIdle()
        assertEquals(listOf("second", "first"), env.results)
    }

    @Test
    fun sequential_event_dispatches_end_of_transaction_handlers_sequentially() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            DelayingSequentialEndOfTransactionHandler(env.results, 100, "first"),
            DelayingSequentialEndOfTransactionHandler(env.results, 0, "second"),
        )
        env.dispatch(TestSequentialDomainEvent("test"))
        env.flushSecondaryWork()
        assertEquals(listOf("first", "second"), env.results)
    }

    @Test
    fun concurrent_event_dispatches_after_transaction_handlers_concurrently() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            DelayingDispatchAfterTransactionHandler(env.results, 100, "first"),
            DelayingDispatchAfterTransactionHandler(env.results, 0, "second"),
        )
        env.dispatch(TestDomainEvent("test"))
        env.flushPostCommitWork()
        advanceUntilIdle()
        assertEquals(listOf("second", "first"), env.results)
    }

    @Test
    fun sequential_event_dispatches_after_transaction_handlers_sequentially() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            DelayingSequentialAfterTransactionHandler(env.results, 100, "first"),
            DelayingSequentialAfterTransactionHandler(env.results, 0, "second"),
        )
        env.dispatch(TestSequentialDomainEvent("test"))
        env.flushPostCommitWork()
        assertEquals(listOf("first", "second"), env.results)
    }

    // =========================================================================
    // ERROR HANDLING STRATEGIES & DEFAULTS
    // =========================================================================

    @Test
    fun domain_events_default_to_fire_and_forget_for_default_phase_handlers() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            ThrowingDomainEventHandler(env.results),
            TestDomainEventHandler(env.results),
        )
        env.dispatch(TestDomainEvent("test"))
        env.flushAllScheduledWork()
        advanceUntilIdle()

        assertEquals(listOf("threw:test", "test"), env.results)
    }

    @Test
    fun domain_events_default_to_fire_and_forget_for_immediate_handlers() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            ThrowingDispatchImmediatelyHandler(env.results),
            TestDispatchImmediatelyHandler(env.results),
        )
        env.dispatch(TestDomainEvent("test"))

        assertEquals(listOf("threw:test", "test"), env.results)
    }

    @Test
    fun fire_and_forget_does_not_propagate_exceptions_and_executes_subsequent_handlers() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            ThrowingFireAndForgetHandler(env.results),
            SucceedingFireAndForgetHandler(env.results),
        )
        env.dispatch(TestFireAndForgetEvent("test"))
        assertEquals(listOf("threw:test", "success:test"), env.results)
    }

    @Test
    fun fail_fast_throws_immediately_on_first_failure_and_halts_execution() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            ThrowingFailFastHandler(env.results),
            SucceedingFailFastHandler(env.results),
        )
        val exception =
            assertFailsWith<TestHandlerException> { env.dispatch(TestFailFastEvent("test")) }
        assertEquals("FailFast handler failed for: test", exception.message)
        assertEquals(listOf("threw:test"), env.results)
    }

    @Test
    fun fail_fast_does_not_throw_when_all_handlers_succeed() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            SucceedingFailFastHandler(env.results),
            SucceedingFailFastHandler(env.results),
        )
        env.dispatch(TestFailFastEvent("test"))
        assertEquals(listOf("success:test", "success:test"), env.results)
    }

    @Test
    fun continue_and_aggregate_runs_all_handlers_and_throws_an_aggregated_exception() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            ThrowingContinueAndAggregateHandler(env.results, "first"),
            SucceedingContinueAndAggregateHandler(env.results, "second"),
            ThrowingContinueAndAggregateHandler(env.results, "third"),
        )
        val exception =
            assertFailsWith<MultipleException> {
                env.dispatch(TestContinueAndAggregateEvent("test"))
            }

        assertEquals(listOf("threw:first", "success:second", "threw:third"), env.results)
        assertEquals(2, exception.exceptions.size)
    }

    @Test
    fun continue_and_aggregate_does_not_throw_when_all_handlers_succeed() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            SucceedingContinueAndAggregateHandler(env.results, "first"),
            SucceedingContinueAndAggregateHandler(env.results, "second"),
        )
        env.dispatch(TestContinueAndAggregateEvent("test"))
        assertEquals(listOf("success:first", "success:second"), env.results)
    }

    // =========================================================================
    // ORTHOGONALITY: ERROR STRATEGY × CONCURRENCY (Sequential)
    // =========================================================================

    @Test
    fun sequential_fail_fast_throws_first_exception_and_stops() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            ThrowingSequentialFailFastHandler(env.results),
            SucceedingSequentialFailFastHandler(env.results),
        )
        val exception =
            assertFailsWith<TestHandlerException> {
                env.dispatch(TestSequentialFailFastEvent("test"))
            }

        assertEquals("FailFast handler failed for: test", exception.message)
        assertEquals(listOf("threw:test"), env.results)
    }

    @Test
    fun sequential_fire_and_forget_does_not_propagate_exceptions() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            ThrowingSequentialFireAndForgetHandler(env.results),
            SucceedingSequentialFireAndForgetHandler(env.results),
        )
        env.dispatch(TestSequentialFireAndForgetEvent("test"))
        assertEquals(listOf("threw:test", "success:test"), env.results)
    }

    @Test
    fun sequential_continue_and_aggregate_runs_all_handlers_then_throws_aggregate() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            ThrowingSequentialContinueAndAggregateHandler(env.results, "first"),
            SucceedingSequentialContinueAndAggregateHandler(env.results, "second"),
            ThrowingSequentialContinueAndAggregateHandler(env.results, "third"),
        )
        val exception =
            assertFailsWith<MultipleException> {
                env.dispatch(TestSequentialContinueAndAggregateEvent("test"))
            }

        assertEquals(listOf("threw:first", "success:second", "threw:third"), env.results)
        assertEquals(2, exception.exceptions.size)
    }

    // =========================================================================
    // ORTHOGONALITY: ERROR STRATEGY × DISPATCH PHASE
    // =========================================================================

    @Test
    fun fire_and_forget_with_dispatch_at_end_of_transaction_does_not_propagate_exceptions() =
        runTest {
            val env = TestEnv(this)
            env.withDomainHandlers(
                ThrowingFireAndForgetAtEndOfTransactionHandler(env.results),
                SucceedingFireAndForgetAtEndOfTransactionHandler(env.results),
            )
            env.dispatch(TestFireAndForgetEvent("test"))
            assertEquals(0, env.results.size)

            env.flushSecondaryWork()
            assertEquals(listOf("threw:test", "success:test"), env.results)
        }

    @Test
    fun fire_and_forget_with_dispatch_after_transaction_does_not_propagate_exceptions() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            ThrowingFireAndForgetAfterTransactionHandler(env.results),
            SucceedingFireAndForgetAfterTransactionHandler(env.results),
        )
        env.dispatch(TestFireAndForgetEvent("test"))
        assertEquals(0, env.results.size)

        env.flushPostCommitWork()
        advanceUntilIdle()
        assertEquals(listOf("threw:test", "success:test"), env.results)
    }

    @Test
    fun fail_fast_with_dispatch_at_end_of_transaction_throws_first_exception() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            ThrowingFailFastAtEndOfTransactionHandler(env.results),
            SucceedingFailFastAtEndOfTransactionHandler(env.results),
        )
        env.dispatch(TestFailFastEvent("test"))
        assertEquals(0, env.results.size)

        assertFailsWith<TestHandlerException> { env.flushSecondaryWork() }
        assertEquals(listOf("threw:test"), env.results)
    }

    @Test
    fun continue_and_aggregate_with_dispatch_at_end_of_transaction_runs_all_handlers() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            SucceedingContinueAndAggregateAtEndOfTransactionHandler(env.results, "first"),
            SucceedingContinueAndAggregateAtEndOfTransactionHandler(env.results, "second"),
        )
        env.dispatch(TestContinueAndAggregateEvent("test"))
        assertEquals(0, env.results.size)

        env.flushSecondaryWork()
        assertEquals(listOf("success:first", "success:second"), env.results)
    }

    @Test
    fun fail_fast_with_mixed_dispatch_phases_throws_for_immediate_handler() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            ThrowingFailFastAtEndOfTransactionHandler(env.results),
            SucceedingFailFastHandler(env.results),
            ThrowingFailFastHandler(env.results),
        )

        assertFailsWith<TestHandlerException> { env.dispatch(TestFailFastEvent("test")) }

        assertEquals(listOf("success:test", "threw:test"), env.results)
        assertEquals(1, env.unitOfWork.secondaryWork.size)

        assertFailsWith<TestHandlerException> { env.flushSecondaryWork() }
    }

    @Test
    fun continue_and_aggregate_with_mixed_dispatch_phases_aggregates_per_dispatch_group() =
        runTest {
            val env = TestEnv(this)
            env.withDomainHandlers(
                ThrowingContinueAndAggregateAtEndOfTransactionHandler(env.results, "deferred"),
                ThrowingContinueAndAggregateHandler(env.results, "immediate-first"),
                ThrowingContinueAndAggregateHandler(env.results, "immediate-second"),
            )

            val immediateException =
                assertFailsWith<MultipleException> {
                    env.dispatch(TestContinueAndAggregateEvent("test"))
                }

            assertEquals(2, immediateException.exceptions.size)
            assertEquals(listOf("threw:immediate-first", "threw:immediate-second"), env.results)
            assertEquals(1, env.unitOfWork.secondaryWork.size) // Deferred is scheduled
        }

    // =========================================================================
    // VALIDATIONS (Dynamic Matrix Testing)
    // =========================================================================

    @Test
    fun it_throws_illegal_state_exception_for_invalid_configurations() = runTest {
        data class InvalidConfig(
            val name: String,
            val handlerFactory: (MutableList<String>) -> EventHandler<*>,
            val event: DomainEvent,
        )

        val invalidConfigs =
            listOf(
                InvalidConfig(
                    "FailFast + PostCommit",
                    { ThrowingFailFastAfterTransactionHandler(it) },
                    TestFailFastEvent("test"),
                ),
                InvalidConfig(
                    "ContinueAndAggregate + PostCommit",
                    { ThrowingContinueAndAggregateAfterTransactionHandler(it, "test") },
                    TestContinueAndAggregateEvent("test"),
                ),
                InvalidConfig(
                    "FailFast + Default Phase",
                    { DefaultPhaseFailFastHandler(it) },
                    TestFailFastEvent("test"),
                ),
            )

        invalidConfigs.forEach { config ->
            val env = TestEnv(this)
            env.withDomainHandlers(config.handlerFactory(env.results))

            val exception =
                assertFailsWith<IllegalStateException>("Expected failure for ${config.name}") {
                    env.dispatch(config.event)
                }
            assertTrue(
                exception.message!!.contains("error strategies") ||
                    exception.message!!.contains("fail-fast")
            )
        }
    }

    @Test
    fun it_allows_fire_and_forget_with_post_commit_as_a_legal_configuration() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(SucceedingFireAndForgetAfterTransactionHandler(env.results))

        env.dispatch(TestFireAndForgetEvent("test"))
        env.flushPostCommitWork()
        advanceUntilIdle()

        assertEquals(listOf("success:test"), env.results)
    }

    // =========================================================================
    // POST-COMMIT / DISPATCHER SCOPE BEHAVIORS
    // =========================================================================

    @Test
    fun fire_and_forget_post_commit_handlers_return_immediately_without_waiting() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            DelayingFireAndForgetAfterTransactionHandler(env.results, 100, "handler-1"),
            DelayingFireAndForgetAfterTransactionHandler(env.results, 100, "handler-2"),
        )

        env.dispatch(TestFireAndForgetEvent("test"))
        env.flushPostCommitWork()

        assertEquals(
            0,
            env.results.size,
            "Handlers are launched in dispatcher scope and should not block",
        )

        advanceUntilIdle()
        assertEquals(2, env.results.size)
    }

    @Test
    fun fire_and_forget_post_commit_handlers_are_concurrent_in_dispatcher_scope() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            DelayingFireAndForgetAfterTransactionHandler(env.results, 100, "first (delayed)"),
            DelayingFireAndForgetAfterTransactionHandler(env.results, 0, "second (fast)"),
        )

        env.dispatch(TestFireAndForgetEvent("test"))
        env.flushPostCommitWork()
        advanceUntilIdle()

        assertEquals(listOf("second (fast)", "first (delayed)"), env.results)
    }

    @Test
    fun fire_and_forget_post_commit_errors_do_not_propagate() = runTest {
        val env = TestEnv(this)
        env.withDomainHandlers(
            ThrowingFireAndForgetAfterTransactionHandler(env.results),
            SucceedingFireAndForgetAfterTransactionHandler(env.results),
        )

        env.dispatch(TestFireAndForgetEvent("test"))
        env.flushPostCommitWork()
        advanceUntilIdle()

        assertEquals(listOf("threw:test", "success:test"), env.results)
    }

    // =========================================================================
    // OBSERVER REGISTRY
    // =========================================================================

    @Test
    fun dispatching_integration_event_emits_to_observer_registry() = runTest {
        val registry = IntegrationEventObserverRegistry()
        val dispatcher =
            EventDispatcher(
                { emptyList() },
                emptyList(),
                this,
                registry,
                invocationContextProvider = { EmptyMiddlewareInvocationContext },
            )

        val received = mutableListOf<TestIntegrationEvent>()
        val flow = registry.observableFor(TestIntegrationEvent::class)
        val job = launch { flow.take(1).toList(received) }
        yield()

        dispatcher.dispatchIntegrationEvent(TestIntegrationEvent("observed"))
        advanceUntilIdle()
        job.join()

        assertEquals("observed", received.single().name)
    }

    // TODO test observer emit is after other dispatches

    // =========================================================================
    // OUTBOX CONTEXT WIRING
    // =========================================================================

    @Test
    fun dispatchDomainEvent_always_builds_context_from_the_unit_of_works_publisher() = runTest {
        val capturingMiddleware = CapturingContextMiddleware()
        val unitOfWorkPublisher = RecordingIntegrationEventPublisher()
        val unitOfWork =
            TestUnitOfWork<Any?>().apply { integrationEventPublisher = unitOfWorkPublisher }
        val dispatcher =
            EventDispatcher(
                { emptyList() },
                listOf(capturingMiddleware),
                this,
                invocationContextProvider = { EmptyMiddlewareInvocationContext },
            )

        dispatcher.dispatchDomainEvent(TestDomainEvent("test"), unitOfWork)

        assertEquals(
            unitOfWorkPublisher,
            capturingMiddleware.capturedContext?.integrationEventPublisher,
        )
    }

    @Test
    fun dispatchDomainEvent_never_falls_back_to_the_provider_context() = runTest {
        val capturingMiddleware = CapturingContextMiddleware()
        val providerOnlyPublisher = RecordingIntegrationEventPublisher()
        val unitOfWork = TestUnitOfWork<Any?>()
        val dispatcher =
            EventDispatcher(
                { emptyList() },
                listOf(capturingMiddleware),
                this,
                invocationContextProvider = { invocationContextOf(providerOnlyPublisher) },
            )

        dispatcher.dispatchDomainEvent(TestDomainEvent("test"), unitOfWork)

        assertEquals(
            unitOfWork.integrationEventPublisher,
            capturingMiddleware.capturedContext?.integrationEventPublisher,
        )
        assertNotEquals(
            providerOnlyPublisher,
            capturingMiddleware.capturedContext?.integrationEventPublisher,
        )
    }
}
