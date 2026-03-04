package com.jimbroze.kbus.core.middleware.middleware.lock

import kotlin.time.Duration

class LockProviderException(message: String) : Exception(message)

interface LockProvider {
    /**
     * Attempts to acquire a lock. If held, waits up to [timeout] to acquire it. Use this when you
     * actually want to own the lock.
     * * @param key The unique identifier for the lock.
     *
     * @param ttl How long the lock is held before the provider auto-expires it.
     * @param timeout How long to wait to acquire the lock before giving up.
     * @return A [LockOutcome] containing the token if successful, or the failure reason.
     */
    suspend fun acquireLock(key: String, ttl: Duration, timeout: Duration): LockOutcome

    /**
     * Releases the lock.
     *
     * @param key The unique identifier for the lock.
     * @param lockToken The token returned by [acquireLock] to ensure ownership.
     * @return true if the lock was successfully released, false if it didn't exist or the token
     *   didn't match.
     */
    suspend fun releaseLock(key: String, lockToken: String): Boolean

    /**
     * Releases the lock regardless of ownership.
     *
     * @param key The unique identifier for the lock.
     * @return true if the lock was successfully released, false if it didn't exist or the token
     *   didn't match.
     */
    suspend fun forceUnlock(key: String): Boolean

    /**
     * Checks if the lock is currently held. Note: Prone to TOCTOU (Time of Check to Time of Use)
     * race conditions. Do not use this to decide whether to run critical business logic.
     */
    suspend fun isLocked(key: String): Boolean

    /**
     * Suspends until the lock is released by its current owner, or the timeout is reached. Use this
     * for "spectator" coroutines that need to know when the bus is free, but DO NOT intend to
     * acquire the lock themselves.
     * * @param key The unique identifier for the lock.
     *
     * @param timeout How long to wait for the lock to become free.
     * @return A [WaitOutcome] indicating success, timeout, or a backend error.
     */
    suspend fun waitForUnlock(key: String, timeout: Duration): WaitOutcome
}

sealed interface LockOutcome {
    data class Success(val lockToken: String) : LockOutcome

    object Timeout : LockOutcome

    data class ProviderError(val exception: Throwable) : LockOutcome
}

sealed interface WaitOutcome {
    object Unlocked : WaitOutcome

    object Timeout : WaitOutcome

    data class ProviderError(val exception: Throwable) : WaitOutcome
}
