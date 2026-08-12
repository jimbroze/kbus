@file:OptIn(ExperimentalTime::class)

package com.jimbroze.kbus.core.infrastructure.lock

import com.jimbroze.kbus.core.infrastructure.cache.FakeDistributedCache
import com.jimbroze.kbus.core.infrastructure.lock.locks.CacheLock
import com.jimbroze.kbus.core.infrastructure.lock.locks.PollingSignallingLock
import com.jimbroze.kbus.testdoubles.TestCoroutineClock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestCoroutineScheduler

class PollingSignallingLockTest : SignallingLockContract() {
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
