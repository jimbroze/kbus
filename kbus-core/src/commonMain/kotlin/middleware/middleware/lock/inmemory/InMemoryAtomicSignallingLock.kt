package com.jimbroze.kbus.core.middleware.middleware.lock.inmemory

import com.jimbroze.kbus.core.middleware.middleware.cache.Cache
import com.jimbroze.kbus.core.middleware.middleware.cache.ThreadSafeMapCache
import com.jimbroze.kbus.core.middleware.middleware.lock.SignallingLock
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalAtomicApi::class, ExperimentalUuidApi::class)
class InMemoryAtomicSignallingLock(
    val cache: Cache<String, ThreadSafeInMemoryLock> = ThreadSafeMapCache(),
    private val scope: CoroutineScope,
) : SignallingLock {
    // extraCapacity is critical here! It provides a buffer so that if a lock
    // is released right before a subscriber attaches, the event isn't dropped,
    // and the publisher doesn't suspend/deadlock waiting for a subscriber.
    private val _unlockEvents = MutableSharedFlow<String>(extraBufferCapacity = 100)

    override val unlockEvents: SharedFlow<String> = _unlockEvents

    override suspend fun publishUnlock(key: String) {
        _unlockEvents.tryEmit(key)
    }

    override suspend fun tryAcquireLock(
        key: String,
        token: String,
        ttl: Duration,
        metadata: String?,
    ): Boolean {
        val lock = cache.getOrPut(key) { ThreadSafeInMemoryLock(key, metadata) }
        lock.addWaiter()

        val acquired = lock.acquire(token)
        if (acquired) {
            scope.launch {
                delay(ttl)
                releaseLock(key, token)
            }
            return true
        } else {
            deregisterWaiter(lock)
            return false
        }
    }

    override suspend fun getLockMetadata(key: String): String? {
        return cache.get(key)?.metadata
    }

    override suspend fun releaseLock(key: String, lockToken: String): Boolean {
        val lock = cache.get(key) ?: return true
        lock.addWaiter()

        return try {
            lock.release(lockToken)
        } finally {
            deregisterWaiter(lock)
        }
    }

    override suspend fun isLocked(key: String): Boolean {
        return this.cache.get(key)?.isLocked == true
    }

    private fun deregisterWaiter(lock: ThreadSafeInMemoryLock) {
        if (lock.removeWaiter() == 0 && !lock.isLocked) {
            cache.removeIfMatching(lock.key, lock)
        }
    }
}
