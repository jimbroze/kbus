package com.jimbroze.kbus.core.middleware.middleware

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.BusResult.Companion.failure
import com.jimbroze.kbus.contracts.result.BusResult.Companion.success
import com.jimbroze.kbus.contracts.result.FailureReason
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.core.middleware.middleware.cache.ThreadSafeMapCache
import com.jimbroze.kbus.core.registry.ReturnCommand
import com.jimbroze.kbus.core.registry.ReturnCommandHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

class LockAwareTimeReturnCommand :
    TimeReturnCommand(), LockingCommand<BusResult<ValueTimeMark, MessageFailure>> {
    override fun busLockedFailure(
        failure: BusLockedFailure
    ): BusResult<ValueTimeMark, MessageFailure> = failure(TestFailure(failure))
}

class TimeReturnCommandHandler :
    CommandHandler<TimeReturnCommand, BusResult<ValueTimeMark, MessageFailure>>() {
    override suspend fun handle(
        message: TimeReturnCommand
    ): BusResult<ValueTimeMark, MessageFailure> {
        val timeSource = TimeSource.Monotonic
        val time = timeSource.markNow()

        return success(time)
    }
}

class TestFailure(override val reason: FailureReason) : MessageFailure

class LockingPrintReturnCommand(val internalCommand: TimeReturnCommand) :
    Command<BusResult<Any, MessageFailure>>(), LockingCommand<BusResult<Any, MessageFailure>> {
    override fun busLockedFailure(failure: BusLockedFailure): BusResult<Any, MessageFailure> =
        failure(TestFailure(failure))
}

class LockingPrintReturnCommandHandler(private val locker: BusLocker) :
    CommandHandler<LockingPrintReturnCommand, BusResult<Any, MessageFailure>>() {
    override suspend fun handle(
        message: LockingPrintReturnCommand
    ): BusResult<Any, MessageFailure> {
        val timeSource = TimeSource.Monotonic
        val preNestTime = timeSource.markNow()

        val result =
            locker.handle(message.internalCommand) { c: TimeReturnCommand ->
                TimeReturnCommandHandler().handle(c)
            }

        val postNestTime = timeSource.markNow()

        return success(
            mapOf("pre-nest" to preNestTime, "nest" to result, "post-nest" to postNestTime)
        )
    }
}

class LockingSleepCommand(
    val sleepFor: Duration,
    val messageData: String,
    override val lockTimeout: Duration? = null,
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
    override val lockTimeout: Duration? = null,
    override val shouldFailOnTimeout: Boolean? = null,
) :
    Command<BusResult<Any, MessageFailure>>(),
    LockingCommand<BusResult<Any, MessageFailure>>,
    LockAdjustMessage {
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
            val locker = BusLocker(ThreadSafeMapCache())

            val result =
                locker.handle(LockingPrintReturnCommand(LockAwareTimeReturnCommand())) {
                    LockingPrintReturnCommandHandler(locker).handle(it)
                }

            // Streamlined assertions: assertIs returns the casted type
            val resultMap = assertIs<Map<String, Any?>>(result.getOrNull())

            val nestException =
                assertIs<TestFailure>(
                    assertIs<BusResult<*, MessageFailure>>(resultMap["nest"]).failureOrNull()
                )

            assertIs<BusLockedFailure>(nestException.reason)
            assertEquals(
                "Cannot handle message as message bus is locked by the same coroutine",
                nestException.reason.message,
            )

            val preNest = assertIs<ValueTimeMark>(resultMap["pre-nest"])
            val postNest = assertIs<ValueTimeMark>(resultMap["post-nest"])
            assertTrue(preNest < postNest)
        }

    @Test
    fun `throws BusLockedException if bus is locked by same coroutine and command is not lock aware`() =
        runTest {
            val locker = BusLocker(ThreadSafeMapCache())

            assertFailsWith<BusLockedException> {
                locker.handle(LockingPrintReturnCommand(TimeReturnCommand())) {
                    LockingPrintReturnCommandHandler(locker).handle(it)
                }
            }
        }

    @Test
    fun `message locker waits to execute command in a different coroutine`() = runTest {
        val locker = BusLocker(ThreadSafeMapCache(), 5.seconds)

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
        val locker = BusLocker(ThreadSafeMapCache())

        val job = async {
            locker.handle(LockingSleepCommand(2.seconds, "data")) {
                // While the handler is running, the mutex should be held
                assertTrue(locker.busLocked, "busLocked should be true while mutex is held")
                LockingSleepCommandHandler().handle(it)
            }
        }

        job.await()

        // After the locking message completes, busLocked should be false
        assertTrue(!locker.busLocked, "busLocked should be false after lock is released")
    }

    @Test
    fun `busLocked is false while a non-locking message is being processed`() = runTest {
        val locker = BusLocker(ThreadSafeMapCache())

        locker.handle(SleepCommand(1.seconds)) {
            // A non-locking message should not report the bus as locked,
            // even though a KeyedLock entry exists in the cache for waiting purposes
            assertTrue(
                !locker.busLocked,
                "busLocked should be false during non-locking message processing",
            )
            SleepCommandHandler().handle(it)
        }
    }

    @Test
    fun `bus locker does not lock bus from a message not implementing locking interface`() =
        runTest {
            val locker = BusLocker(ThreadSafeMapCache())

            locker.handle(SleepCommand(2.seconds)) { SleepCommandHandler().handle(it) }

            assertTrue(!locker.busLocked)
        }

    @Test
    fun `command execution times out using default timeout if bus is locked for too long`() =
        runTest {
            // Default lock timeout is 1 second
            val locker = BusLocker(ThreadSafeMapCache(), 1.seconds)

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
        val locker = BusLocker(ThreadSafeMapCache(), 1.seconds)

        val job1 = async {
            // Holds the lock for 3 seconds
            locker.handle(LockingSleepCommand(3.seconds, "After sleep")) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            // Overrides lock timeout to 5 seconds via LockAdjustMessage
            locker.handle(LockAdjustLockingCommand("After unlock", lockTimeout = 5.seconds)) {
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
        val locker = BusLocker(ThreadSafeMapCache(), 5.seconds)

        val job1 = async {
            // Holds for 3 seconds
            locker.handle(LockingSleepCommand(3.seconds, "After sleep")) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            // Waiting locking message overrides timeout to 1 second
            locker.handle(LockAdjustLockingCommand("After unlock", lockTimeout = 1.seconds)) {
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
    fun `locking message force-unlocks and proceeds when default shouldFailOnTimeout is false`() =
        runTest {
            val locker =
                BusLocker(ThreadSafeMapCache(), 1.seconds, defaultShouldFailOnTimeout = false)

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
    fun `locking message returns failure when default shouldFailOnTimeout is true`() = runTest {
        val locker = BusLocker(ThreadSafeMapCache(), 1.seconds, defaultShouldFailOnTimeout = true)

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
    fun `shouldFailOnTimeout can be overridden to true by locking message`() = runTest {
        // Default is false (force-unlock)
        val locker = BusLocker(ThreadSafeMapCache(), 1.seconds, defaultShouldFailOnTimeout = false)

        val job1 = async {
            locker.handle(LockingSleepCommand(5.seconds, "Job1")) {
                LockingSleepCommandHandler().handle(it)
            }
        }
        val job2 = async {
            // Override shouldFailOnTimeout to true
            locker.handle(LockAdjustLockingCommand("Job2", shouldFailOnTimeout = true)) {
                LockAdjustLockingCommandHandler().handle(it)
            }
        }

        val result2 = job2.await()
        job1.await()

        // Job 2 should fail despite default being false, because it overrode to true
        val failure = assertIs<TestFailure>(result2.failureOrNull())
        assertIs<BusLockedFailure>(failure.reason)
    }

    @Test
    fun `shouldFailOnTimeout can be overridden to false by locking message`() = runTest {
        // Default is true (fail on timeout)
        val locker = BusLocker(ThreadSafeMapCache(), 1.seconds, defaultShouldFailOnTimeout = true)

        val job1 = async {
            locker.handle(LockingSleepCommand(5.seconds, "Job1")) {
                LockingSleepCommandHandler().handle(it)
            }
        }
        val job2 = async {
            // Override shouldFailOnTimeout to false (force-unlock instead)
            locker.handle(LockAdjustLockingCommand("Job2", shouldFailOnTimeout = false)) {
                LockAdjustLockingCommandHandler().handle(it)
            }
        }

        val result2 = job2.await()
        job1.await()

        // Job 2 should succeed because it overrode shouldFailOnTimeout to false
        assertEquals("Job2", result2.getOrNull())
    }
}
