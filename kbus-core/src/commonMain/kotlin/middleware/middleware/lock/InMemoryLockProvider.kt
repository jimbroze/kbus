package com.jimbroze.kbus.core.middleware.middleware.lock

import com.jimbroze.kbus.core.middleware.middleware.cache.Cache
import com.jimbroze.kbus.core.middleware.middleware.cache.ThreadSafeMapCache
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalAtomicApi::class, ExperimentalUuidApi::class)
class InMemoryLockProvider(val cache: Cache<String, KeyedLock> = ThreadSafeMapCache()) :
    LockProvider {

    override suspend fun acquireLock(key: String, ttl: Duration, timeout: Duration): LockOutcome {
        val lock = this.cache.getOrPut(key) { KeyedLock(key, ttl, false) }
        lock.addWaiter()

        val lockToken = Uuid.generateV7().toString()
        return when (lock.lockBus(timeout, lockToken)) {
            is KeyedLockOutcome.Success -> {
                LockOutcome.Success(lockToken)
            }
            is KeyedLockOutcome.RaceConditionFailure ->
                LockOutcome.ProviderError(
                    LockProviderException("Lock was hijacked while acquiring")
                )
            is KeyedLockOutcome.TimeoutFailure -> {
                LockOutcome.Timeout
            }
        }
    }

    override suspend fun releaseLock(key: String, lockToken: String): Boolean {
        val lock = this.cache.get(key) ?: return true
        lock.addWaiter()
        return try {
            if (lock.lockToken.load() == lockToken) {
                lock.unLockBus()
                true
            } else {
                false
            }
        } finally {
            deregisterWaiter(lock)
        }
    }

    override suspend fun forceUnlock(key: String): Boolean {
        val lock = this.cache.get(key) ?: return true
        lock.addWaiter()
        try {
            lock.unLockBus()
            forceUnlock(lock)
            return true
        } finally {
            deregisterWaiter(lock)
        }
    }

    override suspend fun isLocked(key: String): Boolean {
        return this.cache.get(key)?.isLocked == true
    }

    override suspend fun waitForUnlock(key: String, timeout: Duration): WaitOutcome {
        val lock = this.cache.get(key) ?: return WaitOutcome.Unlocked
        lock.addWaiter()
        try {
            return if (lock.waitFullTimeout(timeout)) WaitOutcome.Timeout else WaitOutcome.Unlocked
        } finally {
            deregisterWaiter(lock)
        }
    }

    private fun forceUnlock(lock: KeyedLock) {
        lock.forceUnlock()
        val newLock = reacquireLock(lock)
        if (newLock != null) {
            cache.replaceIfMatching(newLock.key, lock, newLock)
            return
        } else {
            throw LockProviderException("Another process hijacked the lock")
        }
    }

    private fun reacquireLock(oldLock: KeyedLock): KeyedLock? {
        val newLock = KeyedLock(oldLock.key, null, null)
        newLock.addWaiter()

        val swapSucceeded = cache.replaceIfMatching(oldLock.key, oldLock, newLock)
        if (!swapSucceeded) {
            newLock.removeWaiter()
            return null
        }

        //        newLock.lockBus(1.milliseconds)
        return newLock
    }

    private fun deregisterWaiter(lock: KeyedLock) {
        val remainingWaiters = lock.removeWaiter()
        if (remainingWaiters == 0) {
            cache.removeIfMatching(lock.key, lock)
        }
    }
}

@OptIn(ExperimentalAtomicApi::class)
// FIXME these aren't used
// TODO make internal
class KeyedLock(
    val key: String,
    val timeoutOverride: Duration?,
    val shouldFailOnTimeout: Boolean?,
) {
    private val mutex: Mutex = Mutex()
    private val waiters = AtomicInt(0)
    var lockToken = AtomicReference("")
    @Volatile private var _forceUnlocked: Boolean = false

    val isLocked: Boolean
        get() = mutex.isLocked

    val forceUnlocked: Boolean
        get() = this._forceUnlocked

    suspend fun waitFullTimeout(timeout: Duration): Boolean {
        return withTimeoutOrNull(timeout) {
            mutex.withLock {}
            false
        } ?: true
    }

    suspend fun lockBus(timeout: Duration, newLockToken: String): KeyedLockOutcome {
        val lockAcquired =
            withTimeoutOrNull(timeout) {
                mutex.lock()
                lockToken.store(newLockToken)
                true
            }
        return if (lockAcquired == true) {
            KeyedLockOutcome.Success(this@KeyedLock)
        } else {
            if (this.forceUnlocked) {
                this.mutex.unlock()
                KeyedLockOutcome.RaceConditionFailure("Lock was force-unlocked while acquiring")
            }
            KeyedLockOutcome.TimeoutFailure("bus did not unlock in time")
        }
    }

    fun unLockBus() {
        if (this.mutex.isLocked) {
            this.mutex.unlock()
        }
    }

    fun addWaiter(): Int {
        return this.waiters.incrementAndFetch()
    }

    fun removeWaiter(): Int {
        return this.waiters.decrementAndFetch()
    }

    fun forceUnlock() {
        this._forceUnlocked = true
    }
}

sealed interface KeyedLockOutcome {
    data class Success(val activeLock: KeyedLock) : KeyedLockOutcome

    data class TimeoutFailure(val reason: String) : KeyedLockOutcome

    data class RaceConditionFailure(val reason: String) : KeyedLockOutcome
}
