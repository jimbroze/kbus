package com.jimbroze.kbus.core.middleware.middleware.lock.inmemory

import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalAtomicApi::class)
// FIXME use these overrides
// TODO make internal
class KeyedLock(
    val key: String,
    val timeoutOverride: Duration?,
    val shouldFailOnTimeout: Boolean?,
) {
    private val mutex = Mutex()
    private val waiters = AtomicInt(0)
    @Volatile private var activeToken: String? = null

    val isLocked: Boolean
        get() = mutex.isLocked

    suspend fun waitFullTimeout(timeout: Duration): Boolean {
        return withTimeoutOrNull(timeout) {
            mutex.withLock {}
            false
        } ?: true
    }

    suspend fun lockBus(timeout: Duration, newLockToken: String): Boolean {
        @Suppress("SwallowedException")
        return try {
            withTimeout(timeout) {
                mutex.lock(owner = newLockToken)
                activeToken = newLockToken
                true
            }
        } catch (e: TimeoutCancellationException) {
            false
        }
    }

    fun unLockBus(token: String): Boolean {
        if (activeToken == token) {
            activeToken = null
            mutex.unlock(owner = token)
            return true
        }
        return false
    }

    fun addWaiter(): Int = this.waiters.incrementAndFetch()

    fun removeWaiter(): Int = this.waiters.decrementAndFetch()
}
