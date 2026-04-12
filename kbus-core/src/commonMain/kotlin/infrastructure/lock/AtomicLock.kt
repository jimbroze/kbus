package com.jimbroze.kbus.core.infrastructure.lock

import kotlin.time.Duration

interface AtomicLock {
    /**
     * Attempt to acquire a lock.
     *
     * @param key The unique identifier for the lock.
     * @param token A unique token to identify the lock owner
     * @param ttl How long the lock is held before the provider auto-expires it.
     * @return If the lock was acquired
     */
    suspend fun tryAcquireLock(
        key: String,
        token: String,
        ttl: Duration,
        metadata: String? = null,
    ): Boolean

    /**
     * Retrieve the metadata currently associated with the lock. WARNING: This data is stale as soon
     * as it is returned. Do not use this for strict state machine transitions.
     *
     * @param key The unique identifier for the lock.
     * @return The metadata string, or null if the lock is not held or has no data.
     */
    suspend fun getLockMetadata(key: String): String?

    /**
     * Release the lock.
     *
     * @param key The unique identifier for the lock.
     * @param lockToken The token returned by [tryAcquireLock] to ensure ownership.
     * @return true if the lock was successfully released, false if it didn't exist or the token
     *   didn't match.
     */
    suspend fun releaseLock(key: String, lockToken: String): Boolean

    /**
     * Checks if the lock is currently held. Note: Prone to TOCTOU (Time of Check to Time of Use)
     * race conditions. Do not use this to decide whether to run critical business logic.
     *
     * @param key The unique identifier for the lock.
     */
    suspend fun isLocked(key: String): Boolean
}
