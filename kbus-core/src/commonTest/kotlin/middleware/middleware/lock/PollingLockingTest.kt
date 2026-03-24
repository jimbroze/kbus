@file:OptIn(ExperimentalTime::class)

package com.jimbroze.kbus.core.middleware.middleware.lock

import com.jimbroze.kbus.core.infrastructure.lock.SignallingLock
import com.jimbroze.kbus.core.infrastructure.lock.locks.CacheLock
import com.jimbroze.kbus.core.infrastructure.lock.locks.PollingSignallingLock
import com.jimbroze.kbus.core.middleware.middleware.cache.FakeDistributedCache
import com.jimbroze.kbus.testdoubles.TestCoroutineClock
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest

class PollingLockingTest : LockingTestBase() {
    override fun createAtomicLock(
        scheduler: TestCoroutineScheduler
    ): (CoroutineScope) -> SignallingLock = { scope: CoroutineScope ->
        PollingSignallingLock(
            CacheLock(
                FakeDistributedCache { it.toCharArray().concatToString() },
                TestCoroutineClock(scheduler),
            ),
            backgroundScope = scope,
        )
    }

    @Test
    fun verifies_concrete_lock_instance_type() = runTest {
        val lockFactory = createAtomicLock(testScheduler)
        val lock = lockFactory(backgroundScope)
        assertNotNull(lock)
    }
}
