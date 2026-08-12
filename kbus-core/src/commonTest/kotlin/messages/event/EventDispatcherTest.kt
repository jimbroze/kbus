package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.core.fixtures.OtherPrintEventHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.TestDispatchAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.TestDispatchAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.TestDispatchImmediatelyHandler
import com.jimbroze.kbus.core.fixtures.TestDomainEvent
import com.jimbroze.kbus.core.fixtures.TestDomainEventHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EventDispatcherTest {

    @Test
    fun `dispatches an event to every handler registered for it`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.dispatchIntegration(
            StorageEvent("string", env.results),
            PrintEventHandler(),
            OtherPrintEventHandler("string"),
        )
        advanceUntilIdle()

        assertEquals(listOf("string", "string"), env.results)
    }

    @Test
    fun `schedules a domain handler for after the transaction by default`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.withDomainHandlers(TestDomainEventHandler(mutableListOf()))
        env.dispatch(TestDomainEvent("immediate"))
        advanceUntilIdle()

        assertEquals(1, env.unitOfWork.postCommitWork.size)
    }

    @Test
    fun `holds an end-of-transaction handler until the primary work is done`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.withDomainHandlers(TestDispatchAtEndOfTransactionHandler(env.results))
        env.dispatch(TestDomainEvent("after-primary"))

        assertEquals(0, env.results.size, "Should not execute immediately")
        env.flushSecondaryWork()
        assertEquals(listOf("after-primary"), env.results)
    }

    @Test
    fun `holds an after-transaction handler until the transaction commits`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.withDomainHandlers(TestDispatchAfterTransactionHandler(env.results))
        env.dispatch(TestDomainEvent("after-commit"))

        assertEquals(0, env.results.size)
        env.flushPostCommitWork()
        advanceUntilIdle()
        assertEquals(listOf("after-commit"), env.results)
    }

    @Test
    fun `runs every handler of an event whose handlers span different phases`() = runTest {
        val env = EventDispatchEnvironment(this)
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
}
