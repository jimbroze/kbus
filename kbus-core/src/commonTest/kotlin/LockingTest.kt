package com.jimbroze.kbus.core

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.TimeSource

class TimeReturnCommand(val messageData: String, val listStore: MutableList<TimeSource.Monotonic.ValueTimeMark>) : Command()

class TimeReturnCommandHandler : CommandHandler<TimeReturnCommand, TimeSource.Monotonic.ValueTimeMark> {
    override suspend fun handle(message: TimeReturnCommand): TimeSource.Monotonic.ValueTimeMark {
        val timeSource = TimeSource.Monotonic
        val time = timeSource.markNow()

        message.listStore.add(time)

        return time
    }
}

class LockingPrintReturnCommand(val messageData: String, val listStore: MutableList<TimeSource.Monotonic.ValueTimeMark>) : Command(), LockingCommand

class LockingPrintReturnCommandHandler(private val locker: BusLocker) : CommandHandler<LockingPrintReturnCommand, Any> {
    override suspend fun handle(message: LockingPrintReturnCommand): Map<String, Any?> {
        val timeSource = TimeSource.Monotonic
        val preNestTime = timeSource.markNow()

        val result =
            locker.handle(TimeReturnCommand(message.messageData, message.listStore)) { c: TimeReturnCommand ->
                TimeReturnCommandHandler().handle(c)
            }

        val postNestTime = timeSource.markNow()

        return mapOf(
            "pre-nest" to preNestTime,
            "nest" to result,
            "post-nest" to postNestTime
        )
    }
}

class LockingSleepCommand(
    val waitSecs: Float,
    val messageData: String,
    override val lockTimeout: Float? = null,
) : Command(), LockingCommand

class LockingSleepCommandHandler : CommandHandler<LockingSleepCommand, Any> {
    override suspend fun handle(message: LockingSleepCommand): Any {
        delay((1000 * message.waitSecs).toLong())
        return (message.messageData)
    }
}

class SleepCommand(val waitSecs: Float) : Command()

class SleepCommandHandler : CommandHandler<SleepCommand, Unit> {
    override suspend fun handle(message: SleepCommand) {
        delay((1000 * message.waitSecs).toLong())
    }
}

class LockAdjustCommand(
    val messageData: String,
    override val lockTimeout: Float,
) : Command(), LockAdjustMessage

class LockAdjustCommandHandler : CommandHandler<LockAdjustCommand, Any> {
    override suspend fun handle(message: LockAdjustCommand): Any {
        return message.messageData
    }
}

class LockingTest {

    @Test
    fun message_locker_postpones_nested_command_and_returns_unit_instantly() = runTest {
        val locker = BusLocker(TestClock(testScheduler))
        val listStore = mutableListOf<TimeSource.Monotonic.ValueTimeMark>()

        @Suppress("UNCHECKED_CAST")
        val result =
            locker.handle(LockingPrintReturnCommand("Nested call", listStore)) {
                LockingPrintReturnCommandHandler(locker).handle(it)
            } as Map<String, Any?>


        assertIs<Unit>(result["nest"])
        val preNest = result["pre-nest"]
        val postNest = result["post-nest"]
        assertIs<TimeSource.Monotonic.ValueTimeMark>(preNest)
        assertIs<TimeSource.Monotonic.ValueTimeMark>(postNest)
        assertTrue(preNest < postNest)

        assertEquals(1, listStore.count())
        assertTrue(postNest < listStore[0])
    }

    @OptIn(ExperimentalCoroutinesApi::class)
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

            locker.handle(ReturnCommand("After unlock")) {
                ReturnCommandHandler().handle(it)
            }

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
            locker.handle(ReturnCommand("After unlock")) {
                ReturnCommandHandler().handle(it)
            }
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
            locker.handle(
                LockingSleepCommand(0.2f, "After sleep", 0.5f),
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            timeSource.markNow()
        }
        val job2 = async {
            locker.handle(ReturnCommand("After unlock")) {
                ReturnCommandHandler().handle(it)
            }
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
            locker.handle(
                LockingSleepCommand(0.3f, "After sleep", 0.5f),
            ) {
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
