package com.jimbroze.kbus.core.middleware.middleware.lock

import com.jimbroze.kbus.core.fixtures.ConfigurableLockingCommand
import com.jimbroze.kbus.core.fixtures.ConfigurableLockingCommandHandler
import com.jimbroze.kbus.core.fixtures.LockingSleepCommand
import com.jimbroze.kbus.core.fixtures.LockingSleepCommandHandler
import com.jimbroze.kbus.core.fixtures.ReturnCommand
import com.jimbroze.kbus.core.fixtures.ReturnCommandHandler
import com.jimbroze.kbus.core.infrastructure.lock.SignallingLock
import com.jimbroze.kbus.core.infrastructure.lock.locks.InMemoryAtomicSignallingLock
import com.jimbroze.kbus.core.middleware.BusMiddlewareContext
import com.jimbroze.kbus.core.middleware.DefaultMiddlewareInvocationContext
import com.jimbroze.kbus.core.middleware.middleware.LockingMiddleware
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryLockingTest : LockingTestBase() {
    override fun createAtomicLock(
        scheduler: TestCoroutineScheduler
    ): (CoroutineScope) -> SignallingLock = { scope: CoroutineScope ->
        InMemoryAtomicSignallingLock(backgroundScope = scope)
    }

    @Test
    fun verifies_concrete_lock_instance_type() = runTest {
        val lockFactory = createAtomicLock(testScheduler)
        val lock = lockFactory(backgroundScope)
        assertNotNull(lock)
    }

    @Test
    fun `lock auto-expires at exact TTL time and waiting locking message proceeds`() = runTest {
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
                DefaultMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            locker.handle(ConfigurableLockingCommand("Job2"), DefaultMiddlewareInvocationContext) {
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
    fun `non-locking message waiting on expired lock proceeds at exact TTL time`() = runTest {
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
                DefaultMiddlewareInvocationContext,
            ) {
                LockingSleepCommandHandler().handle(it)
            }
            currentTime
        }
        val job2 = async {
            locker.handle(ReturnCommand("Job2"), DefaultMiddlewareInvocationContext) {
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
