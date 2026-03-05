package com.jimbroze.kbus.core.middleware.middleware.lock

import kotlinx.coroutines.flow.SharedFlow

interface SignallingLock : AtomicLock {
    val unlockEvents: SharedFlow<String>

    suspend fun publishUnlock(key: String)
}
