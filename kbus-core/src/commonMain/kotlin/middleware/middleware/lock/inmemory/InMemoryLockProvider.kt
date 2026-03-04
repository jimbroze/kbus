package com.jimbroze.kbus.core.middleware.middleware.lock.inmemory

import com.jimbroze.kbus.core.middleware.middleware.cache.Cache
import com.jimbroze.kbus.core.middleware.middleware.cache.ThreadSafeMapCache
import com.jimbroze.kbus.core.middleware.middleware.lock.LockOutcome
import com.jimbroze.kbus.core.middleware.middleware.lock.LockProvider
import com.jimbroze.kbus.core.middleware.middleware.lock.WaitOutcome
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalAtomicApi::class, ExperimentalUuidApi::class)
class InMemoryLockProvider(
    val cache: Cache<String, KeyedLock> = ThreadSafeMapCache(),
    private val scope: CoroutineScope,
) : LockProvider {

    override suspend fun acquireLock(key: String, ttl: Duration, timeout: Duration): LockOutcome {
        val lock = cache.getOrPut(key) { KeyedLock(key, timeout, false) }
        lock.addWaiter()

        val lockToken = Uuid.generateV7().toString()

        val acquired = lock.lockBus(timeout, lockToken)
        if (acquired) {
            scope.launch {
                delay(ttl)
                releaseLock(key, lockToken)
            }
            return LockOutcome.Success(lockToken)
        } else {
            deregisterWaiter(lock)
            return LockOutcome.Timeout
        }
    }

    override suspend fun releaseLock(key: String, lockToken: String): Boolean {
        val lock = cache.get(key) ?: return true
        lock.addWaiter()

        return try {
            lock.unLockBus(lockToken)
        } finally {
            deregisterWaiter(lock)
        }
    }

    override suspend fun forceUnlock(key: String): Boolean {
        val lock = this.cache.get(key) ?: return true
        lock.addWaiter()

        return try {
            lock.forceUnlock()
            true
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

    private fun deregisterWaiter(lock: KeyedLock) {
        if (lock.removeWaiter() == 0 && !lock.isLocked) {
            cache.removeIfMatching(lock.key, lock)
        }
    }
}
