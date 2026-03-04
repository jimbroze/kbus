package com.jimbroze.kbus.core.middleware.middleware

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.BusResult.Companion.failure
import com.jimbroze.kbus.contracts.result.BusResult.Companion.success
import com.jimbroze.kbus.contracts.result.FailureReason
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.core.middleware.middleware.lock.inmemory.InMemoryLockProvider
import com.jimbroze.kbus.core.registry.ReturnCommand
import com.jimbroze.kbus.core.registry.ReturnCommandHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

open class TimeReturnCommand : Command<BusResult<ValueTimeMark, MessageFailure>>()

class LockAwareTimeReturnCommand(override val lockChannelKey: String? = null) :
    TimeReturnCommand(), LockingCommand<BusResult<ValueTimeMark, MessageFailure>> {
    override fun busLockedFailure(
        failure: BusLockedFailure
    ): BusResult<ValueTimeMark, MessageFailure> = failure(TestFailure(failure))
}

class TimeReturnCommandHandler :
    CommandHandler<TimeReturnCommand, BusResult<ValueTimeMark, MessageFailure>>() {
    override suspend fun handle(
        message: TimeReturnCommand
    ): BusResult<ValueTimeMark, MessageFailure> = success(TimeSource.Monotonic.markNow())
}

class TestFailure(override val reason: FailureReason) : MessageFailure

class NestingLockCommand(
    val internalCommand: TimeReturnCommand,
    override val lockChannelKey: String? = null,
) : Command<BusResult<Any, MessageFailure>>(), LockingCommand<BusResult<Any, MessageFailure>> {
    override fun busLockedFailure(failure: BusLockedFailure): BusResult<Any, MessageFailure> =
        failure(TestFailure(failure))
}

class NestingLockCommandHandler(private val locker: BusLocker) :
    CommandHandler<NestingLockCommand, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(message: NestingLockCommand): BusResult<Any, MessageFailure> {
        val preNestTime = TimeSource.Monotonic.markNow()

        val result =
            locker.handle(message.internalCommand) { c: TimeReturnCommand ->
                TimeReturnCommandHandler().handle(c)
            }

        val postNestTime = TimeSource.Monotonic.markNow()

        return success(
            mapOf("pre-nest" to preNestTime, "nest" to result, "post-nest" to postNestTime)
        )
    }
}

class LockingSleepCommand(
    val sleepFor: Duration,
    val messageData: String,
    override val lockTimeoutOverride: Duration? = null,
    override val lockChannelKey: String? = null,
) : Command<BusResult<Any, MessageFailure>>(), LockingCommand<BusResult<Any, MessageFailure>> {
    override fun busLockedFailure(failure: BusLockedFailure): BusResult<Any, MessageFailure> =
        failure(TestFailure(failure))
}

class LockingSleepCommandHandler :
    CommandHandler<LockingSleepCommand, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(message: LockingSleepCommand): BusResult<Any, MessageFailure> {
        delay(message.sleepFor)
        return success(message.messageData)
    }
}

class SleepCommand(val sleepFor: Duration) : Command<BusResult<Unit, MessageFailure>>()

class SleepCommandHandler : CommandHandler<SleepCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: SleepCommand): BusResult<Unit, MessageFailure> {
        delay(message.sleepFor)
        return success(Unit)
    }
}

class LockAdjustLockingCommand(
    val messageData: String,
    override val lockTimeoutOverride: Duration? = null,
    override val ignoreLockOnTimeoutOverride: Boolean? = null,
    override val lockChannelKey: String? = null,
) : Command<BusResult<Any, MessageFailure>>(), LockingCommand<BusResult<Any, MessageFailure>> {
    override fun busLockedFailure(failure: BusLockedFailure): BusResult<Any, MessageFailure> =
        failure(TestFailure(failure))
}

class LockAdjustLockingCommandHandler :
    CommandHandler<LockAdjustLockingCommand, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(message: LockAdjustLockingCommand): BusResult<Any, MessageFailure> {
        return success(message.messageData)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LockingTest {
    @Test
    fun `message locker returns failure instantly when bus is locked by same coroutine`() =
        runTest {
            val locker = BusLocker(InMemoryLockProvider(scope = backgroundScope), 5.seconds, 30.seconds)

            val result =
                locker.handle(NestingLockCommand(LockAwareTimeReturnCommand())) {
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
            assertTrue(preNest < postNest)
        }

    @Test
    fun `throws BusLockedException if bus is locked by same coroutine and command is not lock aware`() =
        runTest {
            val locker = BusLocker(InMemoryLockProvider(scope = backgroundScope), 5.seconds, 30.seconds)

            assertFailsWith<BusLockedException> {
                locker.handle(NestingLockCommand(TimeReturnCommand())) {
                    NestingLockCommandHandler(locker).handle(it)
                }
            }
        }

    @Test
    fun `message locker waits to execute command in a different coroutine`() = runTest {
        val locker = BusLocker(InMemoryLockProvider(scope = backgroundScope), 5.seconds, 30.seconds)

        // Launch job 1: It locks the bus and delays for 1 second of VIRTUAL time
        val job1 = async {
            locker.handle(LockingSleepCommand(1.seconds, "After sleep")) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }

        // Launch job 2: It will wait for the lock to release
        val job2 = async {
            locker.handle(ReturnCommand("After unlock")) { ReturnCommandHandler().handle(it) }
            currentTime
        }

        val timeJob1Finished = job1.await()
        val timeJob2Finished = job2.await()

        // Assert based on exact virtual time elapsed
        assertEquals(1000L, timeJob1Finished, "Job 1 should finish after 1 second virtual delay")
        assertEquals(
            1000L,
            timeJob2Finished,
            "Job 2 should finish immediately after Job 1 releases lock",
        )
    }

    @Test
    fun `busLocked is true while a locking message holds the mutex`() = runTest {
        val locker = BusLocker(InMemoryLockProvider(scope = backgroundScope), 5.seconds, 30.seconds)

        val job = async {
            locker.handle(LockingSleepCommand(2.seconds, "data")) {
                // While the handler is running, the mutex should be held
                assertTrue(locker.busIsLocked(), "busLocked should be true while mutex is held")
                LockingSleepCommandHandler().handle(it)
            }
        }

        job.await()

        // After the locking message completes, busLocked should be false
        assertFalse(locker.busIsLocked(), "busLocked should be false after lock is released")
    }

    @Test
    fun `busLocked is false during non-locking message handler execution`() = runTest {
        val locker = BusLocker(InMemoryLockProvider(scope = backgroundScope), 5.seconds, 30.seconds)

        locker.handle(SleepCommand(1.seconds)) {
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
    fun `busLocked is false after non-locking message completes`() = runTest {
        val locker = BusLocker(InMemoryLockProvider(scope = backgroundScope), 5.seconds, 30.seconds)

        locker.handle(SleepCommand(2.seconds)) { SleepCommandHandler().handle(it) }

        assertFalse(locker.busIsLocked())
    }

    @Test
    fun `command execution times out using default timeout if bus is locked for too long`() =
        runTest {
            // Default lock timeout is 1 second
            val locker = BusLocker(InMemoryLockProvider(scope = backgroundScope), 1.seconds, 30.seconds)

            val job1 = async {
                // Holds the lock for 5 seconds
                locker.handle(LockingSleepCommand(5.seconds, "After sleep")) {
                    LockingSleepCommandHandler().handle(it)
                }
                currentTime
            }
            val job2 = async {
                // Non-locking message: throws BusLockedException on timeout
                assertFailsWith<BusLockedException> {
                    locker.handle(ReturnCommand("After unlock")) {
                        ReturnCommandHandler().handle(it)
                    }
                }
                currentTime
            }

            val timeJob2Finished = job2.await()
            val timeJob1Finished = job1.await()

            // Job 2 gives up after 1 virtual second (1000ms)
            assertEquals(1000L, timeJob2Finished)
            // Job 1 finishes after its full 5 virtual seconds (5000ms)
            assertEquals(5000L, timeJob1Finished)
        }

    @Test
    fun `locking timeout can be overridden by locking message`() = runTest {
        // Default timeout is 1 second
        val locker = BusLocker(InMemoryLockProvider(scope = backgroundScope), 1.seconds, 30.seconds)

        val job1 = async {
            // Holds the lock for 3 seconds
            locker.handle(LockingSleepCommand(3.seconds, "After sleep")) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            // Overrides lock timeout to 5 seconds via LockAwareMessage
            // FIXME need to add tests for TTL and rename
            locker.handle(
                LockAdjustLockingCommand("After unlock", lockTimeoutOverride = 5.seconds)
            ) {
                LockAdjustLockingCommandHandler().handle(it)
            }
            currentTime
        }

        val timeJob1Finished = job1.await()
        val timeJob2Finished = job2.await()

        // Job 1 finishes after its 3 second sleep
        assertEquals(3000L, timeJob1Finished)
        // Job 2 successfully waits 3 seconds (within its 5s override) instead of timing out at 1s
        assertEquals(3000L, timeJob2Finished)
    }

    @Test
    fun `locking timeout can be overridden by waiting message`() = runTest {
        // Default timeout is 5 seconds
        val locker = BusLocker(InMemoryLockProvider(scope = backgroundScope), 5.seconds, 30.seconds)

        val job1 = async {
            // Holds for 3 seconds
            locker.handle(LockingSleepCommand(3.seconds, "After sleep")) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            // Waiting locking message overrides timeout to 1 second
            locker.handle(
                LockAdjustLockingCommand("After unlock", lockTimeoutOverride = 1.seconds)
            ) {
                LockAdjustLockingCommandHandler().handle(it)
            }
            currentTime
        }

        val timeJob2Finished = job2.await()
        val timeJob1Finished = job1.await()

        // Job 2 times out after 1 second (its override) and force-unlocks
        assertEquals(1000L, timeJob2Finished)
        // Job 1 continues its 3 second sleep
        assertEquals(3000L, timeJob1Finished)
    }

    @Test
    fun `locking message force-unlocks and proceeds when default ignoreLockOnTimeoutOverride is true`() =
        runTest {
            val locker =
                BusLocker(
                    InMemoryLockProvider(scope = backgroundScope),
                    1.seconds,
                    30.seconds,
                    defaultIgnoreLockOnTimeout = true,
                )

            val job1 = async {
                // Holds the lock for 5 seconds
                locker.handle(LockingSleepCommand(5.seconds, "Job1")) {
                    LockingSleepCommandHandler().handle(it)
                }
            }
            val job2 = async {
                // Another locking message: times out after 1s, force-unlocks and proceeds
                locker.handle(LockAdjustLockingCommand("Job2")) {
                    LockAdjustLockingCommandHandler().handle(it)
                }
            }

            val result2 = job2.await()
            job1.await()

            // Job 2 should succeed because it force-unlocked
            assertEquals("Job2", result2.getOrNull())
        }

    @Test
    fun `locking message returns failure when default ignoreLockOnTimeout is false`() = runTest {
        val locker =
            BusLocker(
                InMemoryLockProvider(scope = backgroundScope),
                1.seconds,
                30.seconds,
                defaultIgnoreLockOnTimeout = false,
            )

        val job1 = async {
            // Holds the lock for 5 seconds
            locker.handle(LockingSleepCommand(5.seconds, "Job1")) {
                LockingSleepCommandHandler().handle(it)
            }
        }
        val job2 = async {
            // Another locking message: times out after 1s, should fail
            locker.handle(LockAdjustLockingCommand("Job2")) {
                LockAdjustLockingCommandHandler().handle(it)
            }
        }

        val result2 = job2.await()
        job1.await()

        // Job 2 should have a failure because shouldFailOnTimeout is true
        val failure = assertIs<TestFailure>(result2.failureOrNull())
        val reason = assertIs<BusLockedFailure>(failure.reason)
        assertEquals("Message bus did not unlock in time", reason.message)
    }

    @Test
    fun `ignoreLockOnTimeout can be overridden to false by locking message`() = runTest {
        // Default is false (force-unlock)
        val locker =
            BusLocker(
                InMemoryLockProvider(scope = backgroundScope),
                1.seconds,
                30.seconds,
                defaultIgnoreLockOnTimeout = true,
            )

        val job1 = async {
            locker.handle(LockingSleepCommand(5.seconds, "Job1")) {
                LockingSleepCommandHandler().handle(it)
            }
        }
        val job2 = async {
            // Override ignoreLockOnTimeout to false
            locker.handle(LockAdjustLockingCommand("Job2", ignoreLockOnTimeoutOverride = false)) {
                LockAdjustLockingCommandHandler().handle(it)
            }
        }

        val result2 = job2.await()
        job1.await()

        // Job 2 should fail despite default being true, because it overrode to false
        val failure = assertIs<TestFailure>(result2.failureOrNull())
        assertIs<BusLockedFailure>(failure.reason)
    }

    @Test
    fun `ignoreLockOnTimeout can be overridden to true by locking message`() = runTest {
        // Default is true (fail on timeout)
        val locker =
            BusLocker(
                InMemoryLockProvider(scope = backgroundScope),
                1.seconds,
                30.seconds,
                defaultIgnoreLockOnTimeout = false,
            )

        val job1 = async {
            locker.handle(LockingSleepCommand(5.seconds, "Job1")) {
                LockingSleepCommandHandler().handle(it)
            }
        }
        val job2 = async {
            // Override shouldFailOnTimeout to true (force-unlock instead)
            locker.handle(LockAdjustLockingCommand("Job2", ignoreLockOnTimeoutOverride = true)) {
                LockAdjustLockingCommandHandler().handle(it)
            }
        }

        val result2 = job2.await()
        job1.await()

        // Job 2 should succeed because it overrode shouldFailOnTimeout to false
        assertEquals("Job2", result2.getOrNull())
    }

    @Test
    fun `messages with different lockChannelKeys do not block each other`() = runTest {
        val locker = BusLocker(InMemoryLockProvider(scope = backgroundScope), 5.seconds, 30.seconds)

        val job1 = async {
            locker.handle(LockingSleepCommand(3.seconds, "KeyA", lockChannelKey = "keyA")) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            locker.handle(LockingSleepCommand(1.seconds, "KeyB", lockChannelKey = "keyB")) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }

        val timeJob1Finished = job1.await()
        val timeJob2Finished = job2.await()

        // Both should run concurrently since they use different keys
        assertEquals(3000L, timeJob1Finished, "Job 1 should finish after its own 3s delay")
        assertEquals(1000L, timeJob2Finished, "Job 2 should finish after its own 1s delay")
    }

    @Test
    fun `messages with the same lockChannelKey block each other`() = runTest {
        val locker = BusLocker(InMemoryLockProvider(scope = backgroundScope), 5.seconds, 30.seconds)

        val job1 = async {
            locker.handle(LockingSleepCommand(1.seconds, "First", lockChannelKey = "shared")) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            locker.handle(LockingSleepCommand(1.seconds, "Second", lockChannelKey = "shared")) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }

        val timeJob1Finished = job1.await()
        val timeJob2Finished = job2.await()

        // Job 2 must wait for Job 1 to release the lock before it can proceed
        assertEquals(1000L, timeJob1Finished)
        assertEquals(2000L, timeJob2Finished)
    }

    @Test
    fun `busIsLocked is true only for the locked channel key`() = runTest {
        val locker = BusLocker(InMemoryLockProvider(scope = backgroundScope), 5.seconds, 30.seconds)

        val job = async {
            locker.handle(LockingSleepCommand(2.seconds, "data", lockChannelKey = "myKey")) {
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
    fun `non-locking message only waits for lock on the default channel key`() = runTest {
        val locker = BusLocker(InMemoryLockProvider(scope = backgroundScope), 5.seconds, 30.seconds)

        val job1 = async {
            // Lock on a specific key
            locker.handle(LockingSleepCommand(3.seconds, "KeyA", lockChannelKey = "keyA")) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            // Non-locking message uses the default key (null), so should not be blocked
            locker.handle(ReturnCommand("NoLock")) { ReturnCommandHandler().handle(it) }
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
    fun `nested locking message with a different key succeeds`() = runTest {
        val locker = BusLocker(InMemoryLockProvider(scope = backgroundScope), 5.seconds, 30.seconds)

        val result =
            locker.handle(
                NestingLockCommand(
                    LockAwareTimeReturnCommand(lockChannelKey = "inner"),
                    lockChannelKey = "outer",
                )
            ) {
                NestingLockCommandHandler(locker).handle(it)
            }

        val resultMap = assertIs<Map<String, Any?>>(result.getOrNull())

        // The nested command uses a different key, so it should succeed
        val nestResult = assertIs<BusResult<ValueTimeMark, MessageFailure>>(resultMap["nest"])
        assertTrue(nestResult.isSuccess, "Nested command with different key should succeed")
    }

    @Test
    fun `nested locking message with the same key fails`() = runTest {
        val locker = BusLocker(InMemoryLockProvider(scope = backgroundScope), 5.seconds, 30.seconds)

        val result =
            locker.handle(
                NestingLockCommand(
                    LockAwareTimeReturnCommand(lockChannelKey = "same"),
                    lockChannelKey = "same",
                )
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

    @Test
    fun `multiple different keys can be locked concurrently by different coroutines`() = runTest {
        val locker = BusLocker(InMemoryLockProvider(scope = backgroundScope), 5.seconds, 30.seconds)

        val job1 = async {
            locker.handle(LockingSleepCommand(2.seconds, "A", lockChannelKey = "key1")) {
                // While handling key1, key2 and key3 can also be locked
                assertTrue(locker.busIsLocked("key1"))
                LockingSleepCommandHandler().handle(it)
            }
        }
        val job2 = async {
            locker.handle(LockingSleepCommand(2.seconds, "B", lockChannelKey = "key2")) {
                assertTrue(locker.busIsLocked("key2"))
                LockingSleepCommandHandler().handle(it)
            }
        }
        val job3 = async {
            locker.handle(LockingSleepCommand(2.seconds, "C", lockChannelKey = "key3")) {
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

    // --- Non-in-memory cache tests ---
    // These use CopyingCache to simulate a cache (Redis, DB) that returns deserialized copies
    // on each get, so callers never share the same object reference (and thus the same Mutex).

    //    private fun copyKeyedLock(lock: KeyedLock) =
    //        KeyedLock(lock.key, lock.timeoutOverride, lock.shouldFailOnTimeout)

    //    private fun createCopyingCache() = CopyingCache<String, KeyedLock>(::copyKeyedLock)

    //    @Test
    //    fun `non-in-memory cache - message locker waits to execute command in a different
    // coroutine`() =
    //        runTest {
    //            val locker = BusLocker(createCopyingCache(), 5.seconds)
    //
    //            val job1 = async {
    //                locker.handle(LockingSleepCommand(1.seconds, "After sleep")) {
    //                    LockingSleepCommandHandler().handle(it)
    //                }
    //                currentTime
    //            }
    //
    //            val job2 = async {
    //                locker.handle(ReturnCommand("After unlock")) {
    // ReturnCommandHandler().handle(it) }
    //                currentTime
    //            }
    //
    //            val timeJob1Finished = job1.await()
    //            val timeJob2Finished = job2.await()
    //
    //            assertEquals(
    //                1000L,
    //                timeJob1Finished,
    //                "Job 1 should finish after 1 second virtual delay",
    //            )
    //            assertEquals(
    //                1000L,
    //                timeJob2Finished,
    //                "Job 2 should finish immediately after Job 1 releases lock",
    //            )
    //        }
    //
    //    @Test
    //    fun `non-in-memory cache - busLocked is true while a locking message holds the mutex`() =
    //        runTest {
    //            val locker = BusLocker(createCopyingCache())
    //
    //            val job = async {
    //                locker.handle(LockingSleepCommand(2.seconds, "data")) {
    //                    assertTrue(locker.busIsLocked(), "busLocked should be true while mutex is
    // held")
    //                    LockingSleepCommandHandler().handle(it)
    //                }
    //            }
    //
    //            job.await()
    //
    //            assertFalse(locker.busIsLocked(), "busLocked should be false after lock is
    // released")
    //        }
    //
    //    @Test
    //    fun `non-in-memory cache - busLocked is false after non-locking message completes`() =
    // runTest {
    //        val locker = BusLocker(createCopyingCache())
    //
    //        locker.handle(SleepCommand(2.seconds)) { SleepCommandHandler().handle(it) }
    //
    //        assertFalse(locker.busIsLocked())
    //    }
    //
    //    @Test
    //    fun `non-in-memory cache - command execution times out using default timeout`() = runTest
    // {
    //        val locker = BusLocker(createCopyingCache(), 1.seconds)
    //
    //        val job1 = async {
    //            locker.handle(LockingSleepCommand(5.seconds, "After sleep")) {
    //                LockingSleepCommandHandler().handle(it)
    //            }
    //            currentTime
    //        }
    //        val job2 = async {
    //            assertFailsWith<BusLockedException> {
    //                locker.handle(ReturnCommand("After unlock")) {
    // ReturnCommandHandler().handle(it) }
    //            }
    //            currentTime
    //        }
    //
    //        val timeJob2Finished = job2.await()
    //        val timeJob1Finished = job1.await()
    //
    //        assertEquals(1000L, timeJob2Finished)
    //        assertEquals(5000L, timeJob1Finished)
    //    }
    //
    //    @Test
    //    fun `non-in-memory cache - locking message force-unlocks and proceeds when
    // shouldFailOnTimeout is false`() =
    //        runTest {
    //            val locker =
    //                BusLocker(createCopyingCache(), 1.seconds, defaultShouldFailOnTimeout = false)
    //
    //            val job1 = async {
    //                locker.handle(LockingSleepCommand(5.seconds, "Job1")) {
    //                    LockingSleepCommandHandler().handle(it)
    //                }
    //            }
    //            val job2 = async {
    //                locker.handle(LockAdjustLockingCommand("Job2")) {
    //                    LockAdjustLockingCommandHandler().handle(it)
    //                }
    //            }
    //
    //            val result2 = job2.await()
    //            job1.await()
    //
    //            assertEquals("Job2", result2.getOrNull())
    //        }
    //
    //    @Test
    //    fun `non-in-memory cache - messages with different lockChannelKeys do not block each
    // other`() =
    //        runTest {
    //            val locker = BusLocker(createCopyingCache(), 5.seconds)
    //
    //            val job1 = async {
    //                locker.handle(LockingSleepCommand(3.seconds, "KeyA", lockChannelKey = "keyA"))
    // {
    //                    LockingSleepCommandHandler().handle(it)
    //                }
    //                currentTime
    //            }
    //            val job2 = async {
    //                locker.handle(LockingSleepCommand(1.seconds, "KeyB", lockChannelKey = "keyB"))
    // {
    //                    LockingSleepCommandHandler().handle(it)
    //                }
    //                currentTime
    //            }
    //
    //            val timeJob1Finished = job1.await()
    //            val timeJob2Finished = job2.await()
    //
    //            assertEquals(3000L, timeJob1Finished, "Job 1 should finish after its own 3s
    // delay")
    //            assertEquals(1000L, timeJob2Finished, "Job 2 should finish after its own 1s
    // delay")
    //        }
    //
    //    @Test
    //    fun `non-in-memory cache - messages with the same lockChannelKey block each other`() =
    // runTest {
    //        val locker = BusLocker(createCopyingCache(), 5.seconds)
    //
    //        val job1 = async {
    //            locker.handle(LockingSleepCommand(1.seconds, "First", lockChannelKey = "shared"))
    // {
    //                LockingSleepCommandHandler().handle(it)
    //            }
    //            currentTime
    //        }
    //        val job2 = async {
    //            locker.handle(LockingSleepCommand(1.seconds, "Second", lockChannelKey = "shared"))
    // {
    //                LockingSleepCommandHandler().handle(it)
    //            }
    //            currentTime
    //        }
    //
    //        val timeJob1Finished = job1.await()
    //        val timeJob2Finished = job2.await()
    //
    //        assertEquals(1000L, timeJob1Finished)
    //        assertEquals(2000L, timeJob2Finished)
    //    }
}
