@file:OptIn(ExperimentalTime::class)

package com.jimbroze.kbus.core.middleware

import com.jimbroze.kbus.infrastructure.cache.adapters.ThreadSafeMapCache
import com.jimbroze.kbus.infrastructure.lock.SignallingLock
import com.jimbroze.kbus.infrastructure.lock.adapters.pollingDistributedAtomicLock
import com.jimbroze.kbus.testdoubles.TestCoroutineClock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestCoroutineScheduler

class PollingSignallingLockTest : SignallingLockContract() {
    override fun createAtomicLock(
        scheduler: TestCoroutineScheduler
    ): (CoroutineScope) -> SignallingLock = { scope: CoroutineScope ->
        pollingDistributedAtomicLock(
            cache = ThreadSafeMapCache(),
            clock = TestCoroutineClock(scheduler),
            backgroundScope = scope,
        )
    }
}
