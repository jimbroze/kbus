package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.fixtures.DefaultPhaseFailFastHandler
import com.jimbroze.kbus.core.fixtures.DelayingDispatchAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingDispatchAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingDispatchImmediatelyHandler
import com.jimbroze.kbus.core.fixtures.DelayingDomainEventHandler
import com.jimbroze.kbus.core.fixtures.DelayingFireAndForgetAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.DelayingIntegrationEventHandler
import com.jimbroze.kbus.core.fixtures.DelayingSequentialDomainEventHandler
import com.jimbroze.kbus.core.fixtures.OtherPrintEventHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.SucceedingContinueAndAggregateHandler
import com.jimbroze.kbus.core.fixtures.SucceedingFailFastAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.SucceedingFailFastHandler
import com.jimbroze.kbus.core.fixtures.SucceedingFireAndForgetAfterTransactionHandler
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
import com.jimbroze.kbus.core.fixtures.TestSequentialDomainEvent
import com.jimbroze.kbus.core.fixtures.TestUnitOfWork
import com.jimbroze.kbus.core.fixtures.ThrowingContinueAndAggregateAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.ThrowingContinueAndAggregateHandler
import com.jimbroze.kbus.core.fixtures.ThrowingFailFastAfterTransactionHandler
import com.jimbroze.kbus.core.fixtures.ThrowingFailFastAtEndOfTransactionHandler
import com.jimbroze.kbus.core.fixtures.ThrowingFailFastHandler
import com.jimbroze.kbus.core.fixtures.ThrowingFireAndForgetHandler
import com.jimbroze.kbus.domain.DomainEvent
import io.kotest.core.spec.style.DescribeSpec
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
class EventDispatcherTest :
    DescribeSpec({
        // =========================================================================
        // TEST FIXTURE
        // Abstracts setup, state (results/UoW), and type-erasure casting.
        // =========================================================================
        class TestEnv(val scope: TestScope) {
            val results = mutableListOf<String>()
            val unitOfWork = TestUnitOfWork<Any?>()
            lateinit var dispatcher: EventDispatcher

            fun withDomainHandlers(vararg handlers: EventHandler<*>): TestEnv {
                @Suppress("UNCHECKED_CAST")
                val castedHandlers = handlers.toList() as List<EventHandler<DomainEvent>>
                dispatcher =
                    EventDispatcher({ castedHandlers }, emptyList(), dispatcherScope = scope)
                return this
            }

            suspend fun dispatch(event: DomainEvent) =
                dispatcher.dispatchDomainEvent(event, unitOfWork)

            suspend fun dispatchIntegration(
                event: IntegrationEvent,
                vararg handlers: EventHandler<*>,
            ) {
                @Suppress("UNCHECKED_CAST")
                val castedHandlers = handlers.toList() as List<EventHandler<IntegrationEvent>>
                dispatcher = EventDispatcher({ emptyList() }, emptyList(), dispatcherScope = scope)
                dispatcher.dispatchIntegrationEvent(event, castedHandlers)
            }

            suspend fun flushSecondaryWork() = unitOfWork.secondaryWork.forEach { it.invoke() }

            suspend fun flushPostCommitWork() = unitOfWork.postCommitWork.forEach { it.invoke() }

            suspend fun flushAllScheduledWork() = unitOfWork.executeAllScheduledWork()
        }

        // =========================================================================
        // TEST SUITE
        // =========================================================================

        describe("EventDispatcher Core") {
            it("dispatches event to all handlers") {
                runTest {
                    val env = TestEnv(this)
                    env.dispatchIntegration(
                        StorageEvent("string", env.results),
                        PrintEventHandler(),
                        OtherPrintEventHandler("string"),
                    )
                    advanceUntilIdle()

                    assertEquals(listOf("string", "string"), env.results)
                }
            }
        }

        describe("Dispatch Phases") {
            it("dispatches domain event handler after transaction by default") {
                runTest {
                    val env =
                        TestEnv(this).withDomainHandlers(TestDomainEventHandler(mutableListOf()))
                    env.dispatch(TestDomainEvent("immediate"))
                    advanceUntilIdle()

                    assertEquals(1, env.unitOfWork.postCommitWork.size)
                }
            }

            it("schedules domain event for after primary work (EndOfTransaction)") {
                runTest {
                    val env = TestEnv(this)
                    env.withDomainHandlers(TestDispatchAtEndOfTransactionHandler(env.results))
                    env.dispatch(TestDomainEvent("after-primary"))

                    assertEquals(0, env.results.size, "Should not execute immediately")
                    env.flushSecondaryWork()
                    assertEquals(listOf("after-primary"), env.results)
                }
            }

            it("schedules domain event for after commit") {
                runTest {
                    val env = TestEnv(this)
                    // FIXED: Was incorrectly using TestDispatchAtEndOfTransactionHandler
                    env.withDomainHandlers(TestDispatchAfterTransactionHandler(env.results))
                    env.dispatch(TestDomainEvent("after-commit"))

                    assertEquals(0, env.results.size)
                    env.flushPostCommitWork()
                    advanceUntilIdle()
                    assertEquals(listOf("after-commit"), env.results)
                }
            }

            it("handles multiple handlers with mixed dispatch phases seamlessly") {
                runTest {
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
            }
        }

        describe("Concurrent vs Sequential Dispatch") {
            describe("Default Concurrency") {
                it("dispatches domain events concurrently") {
                    runTest {
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
                }

                it("dispatches integration events asynchronously (fire and forget)") {
                    runTest {
                        val env = TestEnv(this)
                        env.dispatchIntegration(
                            TestIntegrationEvent("test"),
                            DelayingIntegrationEventHandler(env.results, 100, "delayed handler"),
                        )

                        assertEquals(
                            0,
                            env.results.size,
                            "Dispatch returns before handlers complete",
                        )
                        advanceUntilIdle()
                        assertEquals(listOf("delayed handler"), env.results)
                    }
                }
            }

            describe("Sequential Overrides") {
                it("preserves order for sequential domain events regardless of delay") {
                    runTest {
                        val env = TestEnv(this)
                        env.withDomainHandlers(
                            DelayingSequentialDomainEventHandler(
                                env.results,
                                100,
                                "first (delayed)",
                            ),
                            DelayingSequentialDomainEventHandler(env.results, 0, "second (fast)"),
                        )
                        env.dispatch(TestSequentialDomainEvent("test"))
                        env.flushAllScheduledWork()
                        advanceUntilIdle()

                        assertEquals(listOf("first (delayed)", "second (fast)"), env.results)
                    }
                }
            }
        }

        describe("Concurrency × Dispatch Phase") {
            it("concurrent event dispatches immediate handlers concurrently") {
                runTest {
                    val env = TestEnv(this)
                    env.withDomainHandlers(
                        DelayingDispatchImmediatelyHandler(env.results, 100, "first"),
                        DelayingDispatchImmediatelyHandler(env.results, 0, "second"),
                    )
                    env.dispatch(TestDomainEvent("test"))
                    assertEquals(listOf("second", "first"), env.results)
                }
            }

            it("concurrent event dispatches end of transaction handlers concurrently") {
                runTest {
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
            }

            it("concurrent event dispatches after transaction handlers concurrently") {
                runTest {
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
            }
        }

        describe("Error Handling Strategies") {
            describe("Fire And Forget (Default)") {
                it("does not propagate exceptions and executes subsequent handlers") {
                    runTest {
                        val env = TestEnv(this)
                        env.withDomainHandlers(
                            ThrowingFireAndForgetHandler(env.results),
                            SucceedingFireAndForgetHandler(env.results),
                        )
                        env.dispatch(TestFireAndForgetEvent("test"))
                        assertEquals(listOf("threw:test", "success:test"), env.results)
                    }
                }
            }

            describe("Fail Fast") {
                it("throws immediately on first failure and halts execution") {
                    runTest {
                        val env = TestEnv(this)
                        env.withDomainHandlers(
                            ThrowingFailFastHandler(env.results),
                            SucceedingFailFastHandler(env.results),
                        )
                        val exception =
                            assertFailsWith<TestHandlerException> {
                                env.dispatch(TestFailFastEvent("test"))
                            }
                        assertEquals("FailFast handler failed for: test", exception.message)
                        assertEquals(listOf("threw:test"), env.results)
                    }
                }

                it("does not throw when all handlers succeed") {
                    runTest {
                        val env = TestEnv(this)
                        env.withDomainHandlers(
                            SucceedingFailFastHandler(env.results),
                            SucceedingFailFastHandler(env.results),
                        )
                        env.dispatch(TestFailFastEvent("test"))
                        assertEquals(2, env.results.size)
                    }
                }
            }

            describe("Continue And Aggregate") {
                it("runs all handlers and throws an aggregated MultipleException") {
                    runTest {
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

                        assertEquals(
                            listOf("threw:first", "success:second", "threw:third"),
                            env.results,
                        )
                        assertEquals(2, exception.exceptions.size)
                    }
                }
            }
        }

        describe("Error Strategy × Dispatch Phase") {
            it("FailFast with DispatchAtEndOfTransaction throws first exception") {
                runTest {
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
            }

            it("FailFast with mixed dispatch phases throws for immediate handler") {
                runTest {
                    val env = TestEnv(this)
                    env.withDomainHandlers(
                        ThrowingFailFastAtEndOfTransactionHandler(env.results),
                        SucceedingFailFastHandler(env.results),
                        ThrowingFailFastHandler(env.results),
                    )

                    assertFailsWith<TestHandlerException> {
                        env.dispatch(TestFailFastEvent("test"))
                    }

                    assertEquals(listOf("success:test", "threw:test"), env.results)
                    assertEquals(1, env.unitOfWork.secondaryWork.size)

                    assertFailsWith<TestHandlerException> { env.flushSecondaryWork() }
                }
            }
        }

        describe("Post-Commit / Dispatcher Scope Behaviors") {
            it("fire and forget post-commit handlers return immediately without waiting") {
                runTest {
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
            }
        }

        describe("Validations (Illegal Configurations)") {
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
                it("throws IllegalStateException for ${config.name}") {
                    runTest {
                        val env = TestEnv(this)
                        env.withDomainHandlers(config.handlerFactory(env.results))

                        val exception =
                            assertFailsWith<IllegalStateException> { env.dispatch(config.event) }
                        assertTrue(
                            exception.message!!.contains("error strategies") ||
                                exception.message!!.contains("fail-fast")
                        )
                    }
                }
            }

            it("allows FireAndForget with PostCommit (Legal Configuration)") {
                runTest {
                    val env = TestEnv(this)
                    env.withDomainHandlers(
                        SucceedingFireAndForgetAfterTransactionHandler(env.results)
                    )

                    env.dispatch(TestFireAndForgetEvent("test"))
                    env.flushPostCommitWork()
                    advanceUntilIdle()

                    assertEquals(listOf("success:test"), env.results)
                }
            }
        }
    })
