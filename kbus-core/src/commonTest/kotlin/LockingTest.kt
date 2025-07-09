package com.jimbroze.kbus.core

import com.jimbroze.kbus.core.BusResult.Companion.failure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

open class TimeReturnCommand(val listStore: MutableList<ValueTimeMark>) :
    Command<ValueTimeMark, MessageFailure>()

class LockAwareTimeReturnCommand(listStore: MutableList<ValueTimeMark>) :
    TimeReturnCommand(listStore), LockingCommand<ValueTimeMark, MessageFailure> {
    override fun busLockedFailure(failure: BusLockedFailure) = failure(TestFailure(failure))
}

class TimeReturnCommandHandler :
    CommandHandler<TimeReturnCommand, ValueTimeMark, MessageFailure>() {
    override suspend fun handle(
        message: TimeReturnCommand
    ): BusResult<ValueTimeMark, MessageFailure> {
        val timeSource = TimeSource.Monotonic
        val time = timeSource.markNow()

        message.listStore.add(time)

        return success(time)
    }
}

class TestFailure(override val reason: FailureReason) : MessageFailure

class LockingPrintReturnCommand(val internalCommand: TimeReturnCommand) :
    Command<Any, MessageFailure>(), LockingCommand<Any, MessageFailure> {
    override fun busLockedFailure(failure: BusLockedFailure) = failure(TestFailure(failure))
}

class LockingPrintReturnCommandHandler(private val locker: BusLocker) :
    CommandHandler<LockingPrintReturnCommand, Any, MessageFailure>() {
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
    val waitSecs: Float,
    val messageData: String,
    override val lockTimeout: Float? = null,
) : Command<Any, MessageFailure>(), LockingCommand<Any, MessageFailure> {
    override fun busLockedFailure(failure: BusLockedFailure) = failure(TestFailure(failure))
}

class LockingSleepCommandHandler : CommandHandler<LockingSleepCommand, Any, MessageFailure>() {
    override suspend fun handle(message: LockingSleepCommand): BusResult<Any, MessageFailure> {
        delay((1000 * message.waitSecs).toLong())
        return success(message.messageData)
    }
}

class SleepCommand(val waitSecs: Float) : Command<Unit, MessageFailure>()

class SleepCommandHandler : CommandHandler<SleepCommand, Unit, MessageFailure>() {
    override suspend fun handle(message: SleepCommand): BusResult<Unit, MessageFailure> {
        delay((1000 * message.waitSecs).toLong())
        return success()
    }
}

class LockAdjustCommand(val messageData: String, override val lockTimeout: Float) :
    Command<Any, MessageFailure>(), LockAdjustMessage

class LockAdjustCommandHandler : CommandHandler<LockAdjustCommand, Any, MessageFailure>() {
    override suspend fun handle(message: LockAdjustCommand): BusResult<Any, MessageFailure> {
        return success(message.messageData)
    }
}

class LockingTest {
    @Test
    fun message_locker_postpones_nested_command_and_returns_ResultFailure_instantly() = runTest {
        val locker = BusLocker(TestClock(testScheduler))
        val listStore = mutableListOf<ValueTimeMark>()

        val result =
            locker.handle(LockingPrintReturnCommand(LockAwareTimeReturnCommand(listStore))) {
                LockingPrintReturnCommandHandler(locker).handle(it)
            }

        assertIs<BusResult<Any?, MessageFailure>>(result)
        val resultMap = result.getOrNull()
        assertIs<Map<String, Any?>>(resultMap)

        val nestValue = resultMap["nest"]
        val preNest = resultMap["pre-nest"]
        val postNest = resultMap["post-nest"]

        assertIs<BusResult<Any?, MessageFailure>>(nestValue)
        val nestException = nestValue.failureOrNull()
        assertIs<TestFailure>(nestException)
        assertIs<BusLockedFailure>(nestException.reason)
        assertEquals(
            "Cannot handle message as message bus is locked by the same coroutine",
            nestException.reason.message,
        )

        assertIs<ValueTimeMark>(preNest)
        assertIs<ValueTimeMark>(postNest)

        assertTrue(preNest < postNest)

        assertEquals(1, listStore.count())
        assertTrue(postNest < listStore[0])
    }

    @Test
    fun it_throws_busLockedException_if_not_lock_aware() = runTest {
        val locker = BusLocker(TestClock(testScheduler))
        val listStore = mutableListOf<ValueTimeMark>()

        assertFailsWith<BusLockedException> {
            locker.handle(LockingPrintReturnCommand(TimeReturnCommand(listStore))) {
                LockingPrintReturnCommandHandler(locker).handle(it)
            }
        }
    }

    @Test
    fun message_locker_waits_to_execute_command_in_a_different_coroutine() = runTest {
        val locker = BusLocker(TestClock(testScheduler), 10.0f)
        val timeSource = TimeSource.Monotonic
        val job1 = async {
            locker.handle(LockingSleepCommand(0.5f, "After sleep")) {
                LockingSleepCommandHandler().handle(it)
            }

            timeSource.markNow()
        }
        val beforeUnlock = timeSource.markNow()
        val job2 = async {
            locker.handle(ReturnCommand("After unlock")) { ReturnCommandHandler().handle(it) }

            timeSource.markNow()
        }

        val afterSleep = job1.await()
        val afterUnlock = job2.await()

        assertTrue(beforeUnlock < afterSleep)
        assertTrue(afterSleep < afterUnlock)
    }

    @Test
    fun bus_locker_does_not_lock_bus_from_a_message_not_implementing_locking_interface() = runTest {
        val locker = BusLocker(TestClock(testScheduler))
        locker.handle(SleepCommand(0.2f)) { SleepCommandHandler().handle(it) }
        assertTrue(!locker.busLocked)
    }

    @Test
    fun command_execution_times_out_if_bus_is_locked_for_too_long() = runTest {
        val locker = BusLocker(TestClock(testScheduler), 0.1f)
        val timeSource = TimeSource.Monotonic

        val job1 = async {
            locker.handle(LockingSleepCommand(0.5f, "After sleep")) {
                LockingSleepCommandHandler().handle(it)
            }
            timeSource.markNow()
        }
        val job2 = async {
            locker.handle(ReturnCommand("After unlock")) { ReturnCommandHandler().handle(it) }
            timeSource.markNow()
        }

        val afterSleep = job1.await()
        val afterUnlock = job2.await()

        assertTrue(afterUnlock < afterSleep)
    }

    @Test
    fun locking_timeout_can_be_overriden_by_locking_message() = runTest {
        val locker = BusLocker(TestClock(testScheduler), 0.2f)

        val timeSource = TimeSource.Monotonic

        val job1 = async {
            locker.handle(LockingSleepCommand(0.2f, "After sleep", 0.5f)) {
                LockingSleepCommandHandler().handle(it)
            }
            timeSource.markNow()
        }
        val job2 = async {
            locker.handle(ReturnCommand("After unlock")) { ReturnCommandHandler().handle(it) }
            timeSource.markNow()
        }

        val afterSleep = job1.await()
        val afterUnlock = job2.await()

        assertTrue(afterSleep < afterUnlock)
    }

    @Test
    fun locking_timeout_can_be_overriden_by_waiting_message() = runTest {
        val locker = BusLocker(TestClock(testScheduler), 0.1f)

        val timeSource = TimeSource.Monotonic

        val job1 = async {
            locker.handle(LockingSleepCommand(0.3f, "After sleep", 0.5f)) {
                LockingSleepCommandHandler().handle(it)
            }
            timeSource.markNow()
        }
        val job2 = async {
            locker.handle(LockAdjustCommand("After unlock", 0.1f)) {
                LockAdjustCommandHandler().handle(it)
            }
            timeSource.markNow()
        }

        val afterSleep = job1.await()
        val afterUnlock = job2.await()

        assertTrue(afterUnlock < afterSleep)
    }
}
