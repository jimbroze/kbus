package com.jimbroze.kbus.core.messages.event.dispatch

import com.jimbroze.kbus.core.fixtures.DelayingThrowingContinueAndAggregateHandler
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
import com.jimbroze.kbus.core.fixtures.TestDispatchImmediatelyHandler
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEventHandler
import com.jimbroze.kbus.core.fixtures.TestFailFastEvent
import com.jimbroze.kbus.core.fixtures.TestFireAndForgetEvent
import com.jimbroze.kbus.core.fixtures.TestHandlerException
import com.jimbroze.kbus.core.fixtures.TestSequentialContinueAndAggregateEvent
import com.jimbroze.kbus.core.fixtures.TestSequentialFailFastEvent
import com.jimbroze.kbus.core.fixtures.TestSequentialFireAndForgetEvent
import com.jimbroze.kbus.core.fixtures.ThrowingContinueAndAggregateAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.ThrowingContinueAndAggregateHandler
import com.jimbroze.kbus.core.fixtures.ThrowingDispatchImmediatelyHandler
import com.jimbroze.kbus.core.fixtures.ThrowingDomainEventHandler
import com.jimbroze.kbus.core.fixtures.ThrowingFailFastAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.ThrowingFailFastHandler
import com.jimbroze.kbus.core.fixtures.ThrowingFireAndForgetAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.ThrowingFireAndForgetAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.ThrowingFireAndForgetHandler
import com.jimbroze.kbus.core.fixtures.ThrowingSequentialContinueAndAggregateHandler
import com.jimbroze.kbus.core.fixtures.ThrowingSequentialFailFastHandler
import com.jimbroze.kbus.core.fixtures.ThrowingSequentialFireAndForgetHandler
import com.jimbroze.kbus.core.messages.event.routing.AggregateException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EventDispatcherErrorStrategyTest {

    @Test
    fun `swallows a default-phase handler's exception and runs the rest`() = runTest {
        val env = EventDispatchEnvironment(this)
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
    fun `swallows an immediate handler's exception and runs the rest`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.withDomainHandlers(
            ThrowingDispatchImmediatelyHandler(env.results),
            TestDispatchImmediatelyHandler(env.results),
        )
        env.dispatch(TestDomainEvent("test"))

        assertEquals(listOf("threw:test", "test"), env.results)
    }

    @Test
    fun `swallows a fire-and-forget handler's exception and runs the rest`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.withDomainHandlers(
            ThrowingFireAndForgetHandler(env.results),
            SucceedingFireAndForgetHandler(env.results),
        )
        env.dispatch(TestFireAndForgetEvent("test"))
        assertEquals(listOf("threw:test", "success:test"), env.results)
    }

    @Test
    fun `throws on the first fail-fast handler to fail and runs no more`() = runTest {
        val env = EventDispatchEnvironment(this)
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
    fun `throws nothing when every fail-fast handler succeeds`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.withDomainHandlers(
            SucceedingFailFastHandler(env.results),
            SucceedingFailFastHandler(env.results),
        )
        env.dispatch(TestFailFastEvent("test"))
        assertEquals(listOf("success:test", "success:test"), env.results)
    }

    @Test
    fun `runs every aggregating handler and throws their failures together`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.withDomainHandlers(
            ThrowingContinueAndAggregateHandler(env.results, "first"),
            SucceedingContinueAndAggregateHandler(env.results, "second"),
            ThrowingContinueAndAggregateHandler(env.results, "third"),
        )
        val exception =
            assertFailsWith<AggregateException> {
                env.dispatch(TestContinueAndAggregateEvent("test"))
            }

        assertEquals(listOf("threw:first", "success:second", "threw:third"), env.results)
        assertEquals(2, exception.exceptions.size)
    }

    @Test
    fun `collects an aggregating handler's failure raised after a later handler finished`() =
        runTest {
            val env = EventDispatchEnvironment(this)
            env.withDomainHandlers(
                DelayingThrowingContinueAndAggregateHandler(env.results, 100, "first"),
                SucceedingContinueAndAggregateHandler(env.results, "second"),
            )
            val exception =
                assertFailsWith<AggregateException> {
                    env.dispatch(TestContinueAndAggregateEvent("test"))
                }

            assertEquals(listOf("success:second", "threw:first"), env.results)
            assertEquals(1, exception.exceptions.size)
        }

    @Test
    fun `throws nothing when every aggregating handler succeeds`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.withDomainHandlers(
            SucceedingContinueAndAggregateHandler(env.results, "first"),
            SucceedingContinueAndAggregateHandler(env.results, "second"),
        )
        env.dispatch(TestContinueAndAggregateEvent("test"))
        assertEquals(listOf("success:first", "success:second"), env.results)
    }

    @Test
    fun `throws on the first sequential fail-fast handler to fail and runs no more`() = runTest {
        val env = EventDispatchEnvironment(this)
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
    fun `swallows a sequential fire-and-forget handler's exception and runs the rest`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.withDomainHandlers(
            ThrowingSequentialFireAndForgetHandler(env.results),
            SucceedingSequentialFireAndForgetHandler(env.results),
        )
        env.dispatch(TestSequentialFireAndForgetEvent("test"))
        assertEquals(listOf("threw:test", "success:test"), env.results)
    }

    @Test
    fun `runs every sequential aggregating handler and throws their failures together`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.withDomainHandlers(
            ThrowingSequentialContinueAndAggregateHandler(env.results, "first"),
            SucceedingSequentialContinueAndAggregateHandler(env.results, "second"),
            ThrowingSequentialContinueAndAggregateHandler(env.results, "third"),
        )
        val exception =
            assertFailsWith<AggregateException> {
                env.dispatch(TestSequentialContinueAndAggregateEvent("test"))
            }

        assertEquals(listOf("threw:first", "success:second", "threw:third"), env.results)
        assertEquals(2, exception.exceptions.size)
    }

    @Test
    fun `swallows a fire-and-forget failure raised at the end of the transaction`() = runTest {
        val env = EventDispatchEnvironment(this)
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
    fun `swallows a fire-and-forget failure raised after the transaction`() = runTest {
        val env = EventDispatchEnvironment(this)
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
    fun `throws a fail-fast failure raised at the end of the transaction`() = runTest {
        val env = EventDispatchEnvironment(this)
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
    fun `runs every aggregating handler scheduled for the end of the transaction`() = runTest {
        val env = EventDispatchEnvironment(this)
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
    fun `throws for an immediate fail-fast handler before the deferred ones run`() = runTest {
        val env = EventDispatchEnvironment(this)
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
    fun `aggregates failures within a phase rather than across phases`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.withDomainHandlers(
            ThrowingContinueAndAggregateAtEndOfTransactionHandler(env.results, "deferred"),
            ThrowingContinueAndAggregateHandler(env.results, "immediate-first"),
            ThrowingContinueAndAggregateHandler(env.results, "immediate-second"),
        )

        val immediateException =
            assertFailsWith<AggregateException> {
                env.dispatch(TestContinueAndAggregateEvent("test"))
            }

        assertEquals(2, immediateException.exceptions.size)
        assertEquals(listOf("threw:immediate-first", "threw:immediate-second"), env.results)
        assertEquals(1, env.unitOfWork.secondaryWork.size) // Deferred is scheduled
    }
}
