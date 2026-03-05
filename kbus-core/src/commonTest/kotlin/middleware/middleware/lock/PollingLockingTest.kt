package com.jimbroze.kbus.core.middleware.middleware.lock

import com.jimbroze.kbus.core.TestClock
import com.jimbroze.kbus.core.middleware.middleware.cache.CopyingCache
import com.jimbroze.kbus.core.middleware.middleware.lock.locks.CacheLock
import com.jimbroze.kbus.core.middleware.middleware.lock.locks.PollingSignallingLock
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest

class PollingLockingTest : LockingTestBase() {
    override fun createAtomicLock(
        backgroundScope: CoroutineScope,
        scheduler: TestCoroutineScheduler,
    ): SignallingLock =
        PollingSignallingLock(
            CacheLock(CopyingCache { it.toCharArray().concatToString() }, TestClock(scheduler)),
            backgroundScope = backgroundScope,
        )

    @Test
    fun verifies_concrete_lock_instance_type() = runTest {
        val lock = createAtomicLock(backgroundScope, testScheduler)
        assertNotNull(lock)
    }
}
