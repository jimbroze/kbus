package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.core.fixtures.DelayingFireAndForgetAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.SucceedingFireAndForgetAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.TestFireAndForgetEvent
import com.jimbroze.kbus.core.fixtures.ThrowingFireAndForgetAfterTransactionHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EventDispatcherPostCommitTest {

    @Test
    fun `returns without waiting for fire-and-forget handlers dispatched after the transaction`() =
        runTest {
            val env = EventDispatchEnvironment(this)
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
    fun `runs fire-and-forget handlers dispatched after the transaction concurrently`() = runTest {
        val env = EventDispatchEnvironment(this)
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
    fun `swallows a failure from a fire-and-forget handler dispatched after the transaction`() =
        runTest {
            val env = EventDispatchEnvironment(this)
            env.withDomainHandlers(
                ThrowingFireAndForgetAfterTransactionHandler(env.results),
                SucceedingFireAndForgetAfterTransactionHandler(env.results),
            )

            env.dispatch(TestFireAndForgetEvent("test"))
            env.flushPostCommitWork()
            advanceUntilIdle()

            assertEquals(listOf("threw:test", "success:test"), env.results)
        }
}
