package com.jimbroze.kbus.core.infrastructure.lock.inmemory

import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlinx.coroutines.sync.Mutex

@OptIn(ExperimentalAtomicApi::class)
internal class ThreadSafeInMemoryLock(val key: String, val metadata: String?) {
    private val mutex = Mutex()
    private val waiters = AtomicInt(0)
    @Volatile private var activeToken: String? = null

    val isLocked: Boolean
        get() = mutex.isLocked

    suspend fun acquire(lockToken: String): Boolean {
        mutex.lock(owner = lockToken)
        activeToken = lockToken
        return true
    }

    fun release(token: String): Boolean {
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
