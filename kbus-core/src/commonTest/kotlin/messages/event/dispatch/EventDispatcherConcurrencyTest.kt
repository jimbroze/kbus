package com.jimbroze.kbus.core.messages.event.dispatch

import com.jimbroze.kbus.core.fixtures.DelayingDispatchAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingDispatchAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingDispatchImmediatelyHandler
import com.jimbroze.kbus.core.fixtures.DelayingDomainEventHandler
import com.jimbroze.kbus.core.fixtures.DelayingIntegrationEventHandler
import com.jimbroze.kbus.core.fixtures.DelayingSequentialAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingSequentialDomainEventHandler
import com.jimbroze.kbus.core.fixtures.DelayingSequentialEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingSequentialImmediateHandler
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import com.jimbroze.kbus.core.fixtures.TestSequentialDomainEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EventDispatcherConcurrencyTest {

    @Test
    fun `runs the handlers of a domain event concurrently`() = runTest {
        val env = EventDispatchEnvironment(this)
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
    fun `awaits the handlers of a fire-and-forget integration event before returning`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.dispatchIntegration(
            TestIntegrationEvent("test"),
            DelayingIntegrationEventHandler(env.results, 100, "delayed handler"),
        )

        assertEquals(
            listOf("delayed handler"),
            env.results,
            "A fire-and-forget integration event's handlers are awaited before dispatch " +
                "returns, so an inboxed context only acks after they complete",
        )
    }

    @Test
    fun `runs the handlers of an integration event concurrently`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.dispatchIntegration(
            TestIntegrationEvent("test"),
            DelayingIntegrationEventHandler(env.results, 100, "first (delayed)"),
            DelayingIntegrationEventHandler(env.results, 0, "second (fast)"),
        )
        advanceUntilIdle()

        assertEquals(listOf("second (fast)", "first (delayed)"), env.results)
    }

    @Test
    fun `runs the handlers of a sequential domain event in order however long each takes`() =
        runTest {
            val env = EventDispatchEnvironment(this)
            env.withDomainHandlers(
                DelayingSequentialDomainEventHandler(env.results, 100, "first (delayed)"),
                DelayingSequentialDomainEventHandler(env.results, 0, "second (fast)"),
            )
            env.dispatch(TestSequentialDomainEvent("test"))
            env.flushAllScheduledWork()
            advanceUntilIdle()

            assertEquals(listOf("first (delayed)", "second (fast)"), env.results)
        }

    @Test
    fun `runs immediate handlers of a concurrent event concurrently`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.withDomainHandlers(
            DelayingDispatchImmediatelyHandler(env.results, 100, "first"),
            DelayingDispatchImmediatelyHandler(env.results, 0, "second"),
        )
        env.dispatch(TestDomainEvent("test"))
        assertEquals(listOf("second", "first"), env.results)
    }

    @Test
    fun `runs immediate handlers of a sequential event in order`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.withDomainHandlers(
            DelayingSequentialImmediateHandler(env.results, 100, "first"),
            DelayingSequentialImmediateHandler(env.results, 0, "second"),
        )
        env.dispatch(TestSequentialDomainEvent("test"))
        assertEquals(listOf("first", "second"), env.results)
    }

    @Test
    fun `runs end-of-transaction handlers of a concurrent event concurrently`() = runTest {
        val env = EventDispatchEnvironment(this)
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
    fun `runs end-of-transaction handlers of a sequential event in order`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.withDomainHandlers(
            DelayingSequentialEndOfTransactionHandler(env.results, 100, "first"),
            DelayingSequentialEndOfTransactionHandler(env.results, 0, "second"),
        )
        env.dispatch(TestSequentialDomainEvent("test"))
        env.flushSecondaryWork()
        assertEquals(listOf("first", "second"), env.results)
    }

    @Test
    fun `runs after-transaction handlers of a concurrent event concurrently`() = runTest {
        val env = EventDispatchEnvironment(this)
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
    fun `runs after-transaction handlers of a sequential event in order`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.withDomainHandlers(
            DelayingSequentialAfterTransactionHandler(env.results, 100, "first"),
            DelayingSequentialAfterTransactionHandler(env.results, 0, "second"),
        )
        env.dispatch(TestSequentialDomainEvent("test"))
        env.flushPostCommitWork()
        assertEquals(listOf("first", "second"), env.results)
    }
}
