package com.jimbroze.kbus.infrastructure.lock

import kotlinx.coroutines.flow.SharedFlow

interface SignallingLock : AtomicLock {

    /** A flow of unlock-events, where each event is the key of the lock that was released. */
    val unlockEvents: SharedFlow<String>

    /**
     * Signal to subscribers that the lock has been released.
     *
     * @param key The unique identifier for the lock.
     */
    suspend fun publishUnlock(key: String)
}
