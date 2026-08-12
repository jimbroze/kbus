@file:OptIn(ExperimentalTime::class)

package com.jimbroze.kbus.core.middleware.middleware.lock

import com.jimbroze.kbus.core.infrastructure.lock.SignallingLock
import com.jimbroze.kbus.core.infrastructure.lock.locks.CacheLock
import com.jimbroze.kbus.core.infrastructure.lock.locks.PollingSignallingLock
import com.jimbroze.kbus.core.middleware.middleware.cache.FakeDistributedCache
import com.jimbroze.kbus.testdoubles.TestCoroutineClock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestCoroutineScheduler

class PollingSignallingLockTest : LockingMiddlewareContract() {
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
}
