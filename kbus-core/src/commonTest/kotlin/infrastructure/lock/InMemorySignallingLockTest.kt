package com.jimbroze.kbus.core.infrastructure.lock

import com.jimbroze.kbus.core.fixtures.ConfigurableLockingCommand
import com.jimbroze.kbus.core.fixtures.ConfigurableLockingCommandHandler
import com.jimbroze.kbus.core.fixtures.EmptyMiddlewareInvocationContext
import com.jimbroze.kbus.core.fixtures.LockingSleepCommand
import com.jimbroze.kbus.core.fixtures.LockingSleepCommandHandler
import com.jimbroze.kbus.core.fixtures.ReturnCommand
import com.jimbroze.kbus.core.fixtures.ReturnCommandHandler
import com.jimbroze.kbus.core.infrastructure.lock.locks.InMemoryAtomicSignallingLock
import com.jimbroze.kbus.core.middleware.BusMiddlewareContext
import com.jimbroze.kbus.core.middleware.middleware.LockingMiddleware
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class InMemorySignallingLockTest : SignallingLockContract() {
    override fun createAtomicLock(
        scheduler: TestCoroutineScheduler
    ): (CoroutineScope) -> SignallingLock = { scope: CoroutineScope ->
        InMemoryAtomicSignallingLock(backgroundScope = scope)
    }

    @Test
    fun `releases the lock the instant its expiry elapses`() = runTest {
        val locker =
            LockingMiddleware(
                createAtomicLock(testScheduler),
                10.seconds,
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

        assertEquals(
            2000L,
            timeJob2Finished,
            "Job 2 should proceed exactly when lock expires at 2s",
        )
        assertEquals(5000L, timeJob1Finished, "Job 1 should finish after its full 5s sleep")
    }

    @Test
    fun `releases a waiting non-locking command the instant the expiry elapses`() = runTest {
        val locker =
            LockingMiddleware(
                createAtomicLock(testScheduler),
                10.seconds,
                1.seconds, // lock expires after 1 second
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
            locker.handle(ReturnCommand("Job2"), EmptyMiddlewareInvocationContext) {
                ReturnCommandHandler().handle(it)
            }
            currentTime
        }

        val timeJob2Finished = job2.await()
        val timeJob1Finished = job1.await()

        assertEquals(
            1000L,
            timeJob2Finished,
            "Non-locking message should proceed when lock auto-expires at 1s",
        )
        assertEquals(5000L, timeJob1Finished)
    }
}
