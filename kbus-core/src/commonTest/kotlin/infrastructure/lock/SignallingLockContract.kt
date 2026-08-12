package com.jimbroze.kbus.core.infrastructure.lock

import com.jimbroze.kbus.contracts.middleware.BusLockedException
import com.jimbroze.kbus.contracts.middleware.BusLockedFailure
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.core.fixtures.ConfigurableLockingCommand
import com.jimbroze.kbus.core.fixtures.ConfigurableLockingCommandHandler
import com.jimbroze.kbus.core.fixtures.EmptyMiddlewareInvocationContext
import com.jimbroze.kbus.core.fixtures.LockAwareTimeReturnCommand
import com.jimbroze.kbus.core.fixtures.LockingSleepCommand
import com.jimbroze.kbus.core.fixtures.LockingSleepCommandHandler
import com.jimbroze.kbus.core.fixtures.NestingLockCommand
import com.jimbroze.kbus.core.fixtures.NestingLockCommandHandler
import com.jimbroze.kbus.core.fixtures.ReturnCommand
import com.jimbroze.kbus.core.fixtures.ReturnCommandHandler
import com.jimbroze.kbus.core.fixtures.SleepCommand
import com.jimbroze.kbus.core.fixtures.SleepCommandHandler
import com.jimbroze.kbus.core.fixtures.TestFailure
import com.jimbroze.kbus.core.fixtures.TimeReturnCommand
import com.jimbroze.kbus.core.middleware.BusMiddlewareContext
import com.jimbroze.kbus.core.middleware.middleware.LockingMiddleware
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
abstract class SignallingLockContract {
    abstract fun createAtomicLock(
        scheduler: TestCoroutineScheduler
    ): (CoroutineScope) -> SignallingLock

    // --- Reentrant locking (same coroutine) ---

    @Test
    fun `fails a nested lock-aware command immediately when the same coroutine holds the lock`() =
        runTest {
            val locker = LockingMiddleware(createAtomicLock(testScheduler), 5.seconds, 30.seconds)
            locker.onStart(BusMiddlewareContext(backgroundScope))

            val result =
                locker.handle(
                    NestingLockCommand(LockAwareTimeReturnCommand()),
                    EmptyMiddlewareInvocationContext,
                ) {
                    NestingLockCommandHandler(locker).handle(it)
                }

            val resultMap = assertIs<Map<String, Any?>>(result.getOrNull())

            val nestFailure =
                assertIs<TestFailure>(
                    assertIs<BusResult<*, MessageFailure>>(resultMap["nest"]).failureOrNull()
                )

            assertIs<BusLockedFailure>(nestFailure.reason)
            assertEquals(
                "Cannot handle message as message bus is locked by the same coroutine",
                nestFailure.reason.message,
            )

            val preNest = assertIs<ValueTimeMark>(resultMap["pre-nest"])
            val postNest = assertIs<ValueTimeMark>(resultMap["post-nest"])
            assertTrue(preNest <= postNest)
        }

    @Test
    fun `throws when a nested command is not lock aware and the same coroutine holds the lock`() =
        runTest {
            val locker = LockingMiddleware(createAtomicLock(testScheduler), 5.seconds, 30.seconds)
            locker.onStart(BusMiddlewareContext(backgroundScope))

            assertFailsWith<BusLockedException> {
                locker.handle(
                    NestingLockCommand(TimeReturnCommand()),
                    EmptyMiddlewareInvocationContext,
                ) {
                    NestingLockCommandHandler(locker).handle(it)
                }
            }
        }

    // --- Cross-coroutine lock waiting ---

    @Test
    fun `holds a command in another coroutine until the lock is released`() = runTest {
        val locker = LockingMiddleware(createAtomicLock(testScheduler), 5.seconds, 30.seconds)
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(1.seconds, "After sleep"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }

        val job2 = async {
            locker.handle(ReturnCommand("After unlock"), EmptyMiddlewareInvocationContext) {
                ReturnCommandHandler().handle(it)
            }
            currentTime
        }

        val timeJob1Finished = job1.await()
        val timeJob2Finished = job2.await()

        assertEquals(1000L, timeJob1Finished, "Job 1 should finish after 1 second virtual delay")
        assertEquals(
            1000L,
            timeJob2Finished,
            "Job 2 should finish immediately after Job 1 releases lock",
        )
    }

    // --- busIsLocked state ---

    @Test
    fun `reports the bus as locked only while a locking command runs`() = runTest {
        val locker = LockingMiddleware(createAtomicLock(testScheduler), 5.seconds, 30.seconds)
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job = async {
            locker.handle(
                LockingSleepCommand(2.seconds, "data"),
                EmptyMiddlewareInvocationContext,
            ) {
                assertTrue(locker.busIsLocked(), "busLocked should be true while mutex is held")
                LockingSleepCommandHandler().handle(it)
            }
        }

        job.await()

        assertFalse(locker.busIsLocked(), "busLocked should be false after lock is released")
    }

    @Test
    fun `reports the bus as unlocked while a non-locking command runs`() = runTest {
        val locker = LockingMiddleware(createAtomicLock(testScheduler), 5.seconds, 30.seconds)
        locker.onStart(BusMiddlewareContext(backgroundScope))

        locker.handle(SleepCommand(1.seconds), EmptyMiddlewareInvocationContext) {
            // A non-locking message should not report the bus as locked,
            // even though a KeyedLock entry exists in the cache for waiting purposes
            assertFalse(
                locker.busIsLocked(),
                "busLocked should be false during non-locking message processing",
            )
            SleepCommandHandler().handle(it)
        }
    }

    @Test
    fun `reports the bus as unlocked once a non-locking command completes`() = runTest {
        val locker = LockingMiddleware(createAtomicLock(testScheduler), 5.seconds, 30.seconds)
        locker.onStart(BusMiddlewareContext(backgroundScope))

        locker.handle(SleepCommand(2.seconds), EmptyMiddlewareInvocationContext) {
            SleepCommandHandler().handle(it)
        }

        assertFalse(locker.busIsLocked())
    }

    // --- Lock timeout ---

    @Test
    fun `gives up on the default timeout when the lock is held for longer`() = runTest {
        val locker = LockingMiddleware(createAtomicLock(testScheduler), 1.seconds, 30.seconds)
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(5.seconds, "After sleep"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            assertFailsWith<BusLockedException> {
                locker.handle(ReturnCommand("After unlock"), EmptyMiddlewareInvocationContext) {
                    ReturnCommandHandler().handle(it)
                }
            }
            currentTime
        }

        val timeJob2Finished = job2.await()
        val timeJob1Finished = job1.await()

        assertEquals(1000L, timeJob2Finished)
        assertEquals(5000L, timeJob1Finished)
    }

    // --- Lock timeout override (per-message) ---

    @Test
    fun `honours a longer lock timeout requested by the waiting command`() = runTest {
        val locker = LockingMiddleware(createAtomicLock(testScheduler), 1.seconds, 30.seconds)
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(3.seconds, "After sleep"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            locker.handle(
                ConfigurableLockingCommand("After unlock", lockTimeoutOverride = 5.seconds),
                EmptyMiddlewareInvocationContext,
            ) {
                ConfigurableLockingCommandHandler().handle(it)
            }
            currentTime
        }

        val timeJob1Finished = job1.await()
        val timeJob2Finished = job2.await()

        assertEquals(3000L, timeJob1Finished)
        assertEquals(3000L, timeJob2Finished)
    }

    @Test
    fun `honours a shorter lock timeout requested by the waiting command`() = runTest {
        val locker = LockingMiddleware(createAtomicLock(testScheduler), 5.seconds, 30.seconds)
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(3.seconds, "After sleep"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            locker.handle(
                ConfigurableLockingCommand("After unlock", lockTimeoutOverride = 1.seconds),
                EmptyMiddlewareInvocationContext,
            ) {
                ConfigurableLockingCommandHandler().handle(it)
            }
            currentTime
        }

        val timeJob2Finished = job2.await()
        val timeJob1Finished = job1.await()

        assertEquals(1000L, timeJob2Finished)
        assertEquals(3000L, timeJob1Finished)
    }

    // --- ignoreLockOnTimeout behavior ---

    @Test
    fun `forces the lock and proceeds on timeout when configured to ignore it`() = runTest {
        val locker =
            LockingMiddleware(
                createAtomicLock(testScheduler),
                1.seconds,
                30.seconds,
                defaultIgnoreLockOnTimeout = true,
            )
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(5.seconds, "Job1"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
        }
        val job2 = async {
            locker.handle(ConfigurableLockingCommand("Job2"), EmptyMiddlewareInvocationContext) {
                ConfigurableLockingCommandHandler().handle(it)
            }
        }

        val result2 = job2.await()
        job1.await()

        assertEquals("Job2", result2.getOrNull())
    }

    @Test
    fun `fails on timeout when configured to respect the lock`() = runTest {
        val locker =
            LockingMiddleware(
                createAtomicLock(testScheduler),
                1.seconds,
                30.seconds,
                defaultIgnoreLockOnTimeout = false,
            )
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(5.seconds, "Job1"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
        }
        val job2 = async {
            locker.handle(ConfigurableLockingCommand("Job2"), EmptyMiddlewareInvocationContext) {
                ConfigurableLockingCommandHandler().handle(it)
            }
        }

        val result2 = job2.await()
        job1.await()

        val failure = assertIs<TestFailure>(result2.failureOrNull())
        val reason = assertIs<BusLockedFailure>(failure.reason)
        assertEquals("Timed out waiting for message bus to unlock", reason.message)
    }

    @Test
    fun `respects the lock on timeout when the command overrides the default`() = runTest {
        val locker =
            LockingMiddleware(
                createAtomicLock(testScheduler),
                1.seconds,
                30.seconds,
                defaultIgnoreLockOnTimeout = true,
            )
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(5.seconds, "Job1"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
        }
        val job2 = async {
            locker.handle(
                ConfigurableLockingCommand("Job2", ignoreLockOnTimeoutOverride = false),
                EmptyMiddlewareInvocationContext,
            ) {
                ConfigurableLockingCommandHandler().handle(it)
            }
        }

        val result2 = job2.await()
        job1.await()

        val failure = assertIs<TestFailure>(result2.failureOrNull())
        assertIs<BusLockedFailure>(failure.reason)
    }

    @Test
    fun `forces the lock on timeout when the command overrides the default`() = runTest {
        val locker =
            LockingMiddleware(
                createAtomicLock(testScheduler),
                1.seconds,
                30.seconds,
                defaultIgnoreLockOnTimeout = false,
            )
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(5.seconds, "Job1"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
        }
        val job2 = async {
            locker.handle(
                ConfigurableLockingCommand("Job2", ignoreLockOnTimeoutOverride = true),
                EmptyMiddlewareInvocationContext,
            ) {
                ConfigurableLockingCommandHandler().handle(it)
            }
        }

        val result2 = job2.await()
        job1.await()

        assertEquals("Job2", result2.getOrNull())
    }

    // --- Lock channel keys ---

    @Test
    fun `runs commands locking different channels concurrently`() = runTest {
        val locker = LockingMiddleware(createAtomicLock(testScheduler), 5.seconds, 30.seconds)
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(3.seconds, "KeyA", lockChannelKey = "keyA"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            locker.handle(
                LockingSleepCommand(1.seconds, "KeyB", lockChannelKey = "keyB"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }

        val timeJob1Finished = job1.await()
        val timeJob2Finished = job2.await()

        assertEquals(3000L, timeJob1Finished, "Job 1 should finish after its own 3s delay")
        assertEquals(1000L, timeJob2Finished, "Job 2 should finish after its own 1s delay")
    }

    @Test
    fun `serialises commands locking the same channel`() = runTest {
        val locker = LockingMiddleware(createAtomicLock(testScheduler), 5.seconds, 30.seconds)
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(1.seconds, "First", lockChannelKey = "shared"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            locker.handle(
                LockingSleepCommand(1.seconds, "Second", lockChannelKey = "shared"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }

        val timeJob1Finished = job1.await()
        val timeJob2Finished = job2.await()

        assertEquals(1000L, timeJob1Finished)
        assertEquals(2000L, timeJob2Finished)
    }

    @Test
    fun `reports only the channel a command locked as locked`() = runTest {
        val locker = LockingMiddleware(createAtomicLock(testScheduler), 5.seconds, 30.seconds)
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job = async {
            locker.handle(
                LockingSleepCommand(2.seconds, "data", lockChannelKey = "myKey"),
                EmptyMiddlewareInvocationContext,
            ) {
                assertTrue(locker.busIsLocked("myKey"), "myKey channel should be locked")
                assertFalse(locker.busIsLocked("otherKey"), "otherKey channel should not be locked")
                assertFalse(locker.busIsLocked(), "default channel should not be locked")
                LockingSleepCommandHandler().handle(it)
            }
        }

        job.await()

        assertFalse(
            locker.busIsLocked("myKey"),
            "myKey channel should be unlocked after completion",
        )
    }

    @Test
    fun `lets a non-locking command through while another channel is locked`() = runTest {
        val locker = LockingMiddleware(createAtomicLock(testScheduler), 5.seconds, 30.seconds)
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(3.seconds, "KeyA", lockChannelKey = "keyA"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            locker.handle(ReturnCommand("NoLock"), EmptyMiddlewareInvocationContext) {
                ReturnCommandHandler().handle(it)
            }
            currentTime
        }

        val timeJob1Finished = job1.await()
        val timeJob2Finished = job2.await()

        assertEquals(3000L, timeJob1Finished)
        assertEquals(
            0L,
            timeJob2Finished,
            "Non-locking message should not wait for a different key",
        )
    }

    @Test
    fun `admits a nested command locking a different channel`() = runTest {
        val locker = LockingMiddleware(createAtomicLock(testScheduler), 5.seconds, 30.seconds)
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val result =
            locker.handle(
                NestingLockCommand(
                    LockAwareTimeReturnCommand(lockChannelKey = "inner"),
                    lockChannelKey = "outer",
                ),
                EmptyMiddlewareInvocationContext,
            ) {
                NestingLockCommandHandler(locker).handle(it)
            }

        val resultMap = assertIs<Map<String, Any?>>(result.getOrNull())

        val nestResult = assertIs<BusResult<ValueTimeMark, MessageFailure>>(resultMap["nest"])
        assertTrue(nestResult.isSuccess, "Nested command with different key should succeed")
    }

    @Test
    fun `fails a nested command locking the channel already held`() = runTest {
        val locker = LockingMiddleware(createAtomicLock(testScheduler), 5.seconds, 30.seconds)
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val result =
            locker.handle(
                NestingLockCommand(
                    LockAwareTimeReturnCommand(lockChannelKey = "same"),
                    lockChannelKey = "same",
                ),
                EmptyMiddlewareInvocationContext,
            ) {
                NestingLockCommandHandler(locker).handle(it)
            }

        val resultMap = assertIs<Map<String, Any?>>(result.getOrNull())

        val nestFailure =
            assertIs<TestFailure>(
                assertIs<BusResult<*, MessageFailure>>(resultMap["nest"]).failureOrNull()
            )

        assertIs<BusLockedFailure>(nestFailure.reason)
        assertEquals(
            "Cannot handle message as message bus is locked by the same coroutine",
            nestFailure.reason.message,
        )
    }

    // --- Lock behavior after timeout (skip/fail continuity) ---

    @Test
    fun `keeps the original lock in force for a later locking command after one forces past it`() =
        runTest {
            val locker =
                LockingMiddleware(
                    createAtomicLock(testScheduler),
                    1.seconds,
                    30.seconds,
                    defaultIgnoreLockOnTimeout = true,
                )
            locker.onStart(BusMiddlewareContext(backgroundScope))

            val job1 = async {
                locker.handle(
                    LockingSleepCommand(5.seconds, "Job1"),
                    EmptyMiddlewareInvocationContext,
                ) {
                    LockingSleepCommandHandler().handle(it)
                }
                currentTime
            }
            val job2 = async {
                locker.handle(
                    ConfigurableLockingCommand("Job2"),
                    EmptyMiddlewareInvocationContext,
                ) {
                    ConfigurableLockingCommandHandler().handle(it)
                }
                currentTime
            }
            val job3 = async {
                locker.handle(
                    ConfigurableLockingCommand("Job3", ignoreLockOnTimeoutOverride = false),
                    EmptyMiddlewareInvocationContext,
                ) {
                    ConfigurableLockingCommandHandler().handle(it)
                }
                currentTime
            }

            val timeJob2Finished = job2.await()
            val timeJob3Finished = job3.await()
            val timeJob1Finished = job1.await()

            assertEquals(1000L, timeJob2Finished, "Job 2 should skip lock after 1s timeout")
            assertEquals(1000L, timeJob3Finished, "Job 3 should time out after 1s")
            assertEquals(5000L, timeJob1Finished)

            val result3 =
                locker.handle(
                    ConfigurableLockingCommand("Job3-verify", ignoreLockOnTimeoutOverride = false),
                    EmptyMiddlewareInvocationContext,
                ) {
                    ConfigurableLockingCommandHandler().handle(it)
                }
            assertEquals("Job3-verify", result3.getOrNull())
        }

    @Test
    fun `keeps the original lock in force for a later non-locking command after one forces past it`() =
        runTest {
            val locker =
                LockingMiddleware(
                    createAtomicLock(testScheduler),
                    1.seconds,
                    30.seconds,
                    defaultIgnoreLockOnTimeout = true,
                )
            locker.onStart(BusMiddlewareContext(backgroundScope))

            val job1 = async {
                locker.handle(
                    LockingSleepCommand(5.seconds, "Job1"),
                    EmptyMiddlewareInvocationContext,
                ) {
                    LockingSleepCommandHandler().handle(it)
                }
                currentTime
            }
            val job2 = async {
                locker.handle(
                    ConfigurableLockingCommand("Job2"),
                    EmptyMiddlewareInvocationContext,
                ) {
                    ConfigurableLockingCommandHandler().handle(it)
                }
                currentTime
            }
            val job3 = async {
                val result =
                    locker.handle(ReturnCommand("Job3"), EmptyMiddlewareInvocationContext) {
                        ReturnCommandHandler().handle(it)
                    }
                assertEquals("Job3", result.getOrNull())
                currentTime
            }

            val timeJob2Finished = job2.await()
            val timeJob3Finished = job3.await()
            val timeJob1Finished = job1.await()

            assertEquals(1000L, timeJob2Finished, "Job 2 should skip lock after 1s timeout")
            assertEquals(1000L, timeJob3Finished, "Job 3 should skip lock after 1s timeout")
            assertEquals(5000L, timeJob1Finished)
        }

    @Test
    fun `keeps the original lock in force after a waiting command fails on timeout`() = runTest {
        val locker =
            LockingMiddleware(
                createAtomicLock(testScheduler),
                1.seconds,
                30.seconds,
                defaultIgnoreLockOnTimeout = false,
            )
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(3.seconds, "Job1"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            locker.handle(ConfigurableLockingCommand("Job2"), EmptyMiddlewareInvocationContext) {
                ConfigurableLockingCommandHandler().handle(it)
            }
        }
        val job3 = async {
            locker.handle(ConfigurableLockingCommand("Job3"), EmptyMiddlewareInvocationContext) {
                ConfigurableLockingCommandHandler().handle(it)
            }
        }

        val result2 = job2.await()
        val result3 = job3.await()
        val timeJob1Finished = job1.await()

        assertEquals(3000L, timeJob1Finished)

        assertIs<BusLockedFailure>(assertIs<TestFailure>(result2.failureOrNull()).reason)
        assertIs<BusLockedFailure>(assertIs<TestFailure>(result3.failureOrNull()).reason)
    }

    @Test
    fun `locks and unlocks cleanly again once the original lock is released`() = runTest {
        val locker =
            LockingMiddleware(
                createAtomicLock(testScheduler),
                1.seconds,
                30.seconds,
                defaultIgnoreLockOnTimeout = true,
            )
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(2.seconds, "Job1"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            locker.handle(ConfigurableLockingCommand("Job2"), EmptyMiddlewareInvocationContext) {
                ConfigurableLockingCommandHandler().handle(it)
            }
            currentTime
        }

        val timeJob2Finished = job2.await()
        val timeJob1Finished = job1.await()

        assertEquals(1000L, timeJob2Finished)
        assertEquals(2000L, timeJob1Finished)

        val result3 =
            locker.handle(
                LockingSleepCommand(1.seconds, "Job3"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
        assertEquals("Job3", result3.getOrNull())
        assertFalse(locker.busIsLocked(), "Bus should be unlocked after Job3 completes")
    }

    // --- Lock auto-expiry (TTL) ---

    @Test
    fun `releases the lock once its expiry elapses so a waiting command proceeds`() = runTest {
        val locker =
            LockingMiddleware(
                createAtomicLock(testScheduler),
                10.seconds, // timeout long enough to not interfere
                2.seconds, // lock expires after 2 seconds
            )
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(5.seconds, "Job1"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            locker.handle(ConfigurableLockingCommand("Job2"), EmptyMiddlewareInvocationContext) {
                ConfigurableLockingCommandHandler().handle(it)
            }
            currentTime
        }

        val timeJob2Finished = job2.await()
        val timeJob1Finished = job1.await()

        assertTrue(
            timeJob2Finished < timeJob1Finished,
            "Job 2 ($timeJob2Finished) should proceed before Job 1 finishes ($timeJob1Finished)",
        )
        assertEquals(5000L, timeJob1Finished, "Job 1 should finish after its full 5s sleep")
    }

    @Test
    fun `reports the bus as unlocked once the lock expiry elapses`() = runTest {
        val locker =
            LockingMiddleware(
                createAtomicLock(testScheduler),
                10.seconds,
                1.seconds, // lock expires after 1 second
            )
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job = async {
            locker.handle(
                LockingSleepCommand(3.seconds, "data"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
        }

        delay(2.seconds)
        assertFalse(locker.busIsLocked(), "busIsLocked should be false after lock auto-expires")

        job.await()
    }

    @Test
    fun `releases the lock when the handler finishes before the expiry elapses`() = runTest {
        val locker =
            LockingMiddleware(
                createAtomicLock(testScheduler),
                10.seconds,
                5.seconds, // lock expires after 5 seconds
            )
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(1.seconds, "Job1"),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            locker.handle(ConfigurableLockingCommand("Job2"), EmptyMiddlewareInvocationContext) {
                ConfigurableLockingCommandHandler().handle(it)
            }
            currentTime
        }

        val timeJob1Finished = job1.await()
        val timeJob2Finished = job2.await()

        assertEquals(1000L, timeJob1Finished)
        assertEquals(1000L, timeJob2Finished, "Job 2 should proceed after normal unlock at 1s")
    }

    @Test
    fun `locks several channels at once from different coroutines`() = runTest {
        val locker = LockingMiddleware(createAtomicLock(testScheduler), 5.seconds, 30.seconds)
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(2.seconds, "A", lockChannelKey = "key1"),
                EmptyMiddlewareInvocationContext,
            ) {
                assertTrue(locker.busIsLocked("key1"))
                LockingSleepCommandHandler().handle(it)
            }
        }
        val job2 = async {
            locker.handle(
                LockingSleepCommand(2.seconds, "B", lockChannelKey = "key2"),
                EmptyMiddlewareInvocationContext,
            ) {
                assertTrue(locker.busIsLocked("key2"))
                LockingSleepCommandHandler().handle(it)
            }
        }
        val job3 = async {
            locker.handle(
                LockingSleepCommand(2.seconds, "C", lockChannelKey = "key3"),
                EmptyMiddlewareInvocationContext,
            ) {
                assertTrue(locker.busIsLocked("key3"))
                LockingSleepCommandHandler().handle(it)
            }
        }

        val r1 = job1.await()
        val r2 = job2.await()
        val r3 = job3.await()

        assertEquals("A", r1.getOrNull())
        assertEquals("B", r2.getOrNull())
        assertEquals("C", r3.getOrNull())

        assertFalse(locker.busIsLocked("key1"))
        assertFalse(locker.busIsLocked("key2"))
        assertFalse(locker.busIsLocked("key3"))
    }

    // --- Lock data inheritance (override chain) ---
    // When a locking message stores overrides in lock data, subsequent non-locking
    // messages inherit those values unless they provide their own overrides.

    @Test
    fun `applies the lock holder's request to force the lock to a later non-locking command`() =
        runTest {
            val locker =
                LockingMiddleware(
                    createAtomicLock(testScheduler),
                    1.seconds,
                    30.seconds,
                    defaultIgnoreLockOnTimeout = false,
                )
            locker.onStart(BusMiddlewareContext(backgroundScope))

            val job1 = async {
                locker.handle(
                    LockingSleepCommand(5.seconds, "Job1", ignoreLockOnTimeoutOverride = true),
                    EmptyMiddlewareInvocationContext,
                ) {
                    LockingSleepCommandHandler().handle(it)
                }
                currentTime
            }
            val job2 = async {
                val result =
                    locker.handle(ReturnCommand("Job2"), EmptyMiddlewareInvocationContext) {
                        ReturnCommandHandler().handle(it)
                    }
                assertEquals("Job2", result.getOrNull())
                currentTime
            }

            val timeJob2Finished = job2.await()
            val timeJob1Finished = job1.await()

            assertEquals(1000L, timeJob2Finished, "Job 2 should skip lock after 1s timeout")
            assertEquals(5000L, timeJob1Finished)
        }

    @Test
    fun `applies the lock holder's request to respect the lock to a later non-locking command`() =
        runTest {
            val locker =
                LockingMiddleware(
                    createAtomicLock(testScheduler),
                    1.seconds,
                    30.seconds,
                    defaultIgnoreLockOnTimeout = true,
                )
            locker.onStart(BusMiddlewareContext(backgroundScope))

            val job1 = async {
                locker.handle(
                    LockingSleepCommand(5.seconds, "Job1", ignoreLockOnTimeoutOverride = false),
                    EmptyMiddlewareInvocationContext,
                ) {
                    LockingSleepCommandHandler().handle(it)
                }
                currentTime
            }
            val job2 = async {
                assertFailsWith<BusLockedException> {
                    locker.handle(ReturnCommand("Job2"), EmptyMiddlewareInvocationContext) {
                        ReturnCommandHandler().handle(it)
                    }
                }
                currentTime
            }

            val timeJob2Finished = job2.await()
            val timeJob1Finished = job1.await()

            assertEquals(1000L, timeJob2Finished, "Job 2 should fail after 1s timeout")
            assertEquals(5000L, timeJob1Finished)
        }

    @Test
    fun `applies the lock holder's timeout to a later non-locking command`() = runTest {
        val locker =
            LockingMiddleware(
                createAtomicLock(testScheduler),
                1.seconds,
                30.seconds,
                defaultIgnoreLockOnTimeout = false,
            )
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(2.seconds, "Job1", lockTimeoutOverride = 3.seconds),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            val result =
                locker.handle(ReturnCommand("Job2"), EmptyMiddlewareInvocationContext) {
                    ReturnCommandHandler().handle(it)
                }
            assertEquals("Job2", result.getOrNull())
            currentTime
        }

        val timeJob2Finished = job2.await()
        val timeJob1Finished = job1.await()

        assertEquals(2000L, timeJob2Finished, "Job 2 should succeed after lock releases at 2s")
        assertEquals(2000L, timeJob1Finished)
    }

    @Test
    fun `prefers a waiting command's own force-lock policy over the lock holder's`() = runTest {
        val locker =
            LockingMiddleware(
                createAtomicLock(testScheduler),
                1.seconds,
                30.seconds,
                defaultIgnoreLockOnTimeout = true,
            )
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(5.seconds, "Job1", ignoreLockOnTimeoutOverride = true),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            locker.handle(
                ConfigurableLockingCommand("Job2", ignoreLockOnTimeoutOverride = false),
                EmptyMiddlewareInvocationContext,
            ) {
                ConfigurableLockingCommandHandler().handle(it)
            }
        }

        val result2 = job2.await()
        val timeJob1Finished = job1.await()

        val failure = assertIs<TestFailure>(result2.failureOrNull())
        assertIs<BusLockedFailure>(failure.reason)
        assertEquals(5000L, timeJob1Finished)
    }

    @Test
    fun `prefers a waiting command's own timeout over the lock holder's`() = runTest {
        val locker =
            LockingMiddleware(
                createAtomicLock(testScheduler),
                5.seconds,
                30.seconds,
                defaultIgnoreLockOnTimeout = false,
            )
        locker.onStart(BusMiddlewareContext(backgroundScope))

        val job1 = async {
            locker.handle(
                LockingSleepCommand(4.seconds, "Job1", lockTimeoutOverride = 3.seconds),
                EmptyMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            locker.handle(
                ConfigurableLockingCommand("Job2", lockTimeoutOverride = 1.seconds),
                EmptyMiddlewareInvocationContext,
            ) {
                ConfigurableLockingCommandHandler().handle(it)
            }
        }

        val result2 = job2.await()
        val timeJob1Finished = job1.await()

        val failure = assertIs<TestFailure>(result2.failureOrNull())
        assertIs<BusLockedFailure>(failure.reason)
        assertEquals(4000L, timeJob1Finished)
    }
}
