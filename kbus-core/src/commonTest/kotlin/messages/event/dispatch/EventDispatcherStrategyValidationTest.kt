package com.jimbroze.kbus.core.messages.event.dispatch

import com.jimbroze.kbus.core.fixtures.DefaultPhaseFailFastHandler
import com.jimbroze.kbus.core.fixtures.SucceedingFireAndForgetAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.TestContinueAndAggregateEvent
import com.jimbroze.kbus.core.fixtures.TestFailFastEvent
import com.jimbroze.kbus.core.fixtures.TestFireAndForgetEvent
import com.jimbroze.kbus.core.fixtures.ThrowingContinueAndAggregateAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.ThrowingFailFastAfterTransactionHandler
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EventDispatcherStrategyValidationTest {

    @Test
    fun `refuses an error strategy the dispatch phase cannot honour`() = runTest {
        data class InvalidConfig(
            val name: String,
            val handlerFactory: (MutableList<String>) -> DomainEventHandler<*>,
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
            val env = EventDispatchEnvironment(this)
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
    fun `accepts a fire-and-forget handler dispatched after the transaction`() = runTest {
        val env = EventDispatchEnvironment(this)
        env.withDomainHandlers(SucceedingFireAndForgetAfterTransactionHandler(env.results))

        env.dispatch(TestFireAndForgetEvent("test"))
        env.flushPostCommitWork()
        advanceUntilIdle()

        assertEquals(listOf("success:test"), env.results)
    }
}
