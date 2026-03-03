package com.jimbroze.kbus.core.middleware.middleware

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.contracts.common.ResultReturningMessage
import com.jimbroze.kbus.contracts.result.FailureReason
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareHandler
import com.jimbroze.kbus.core.middleware.middleware.cache.Cache
import com.jimbroze.kbus.core.middleware.middleware.cache.ThreadSafeMapCache
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// TODO fix for JS Browser
// TODO create queue?
interface LockAwareMessage {
    val shouldLockBus: Boolean
    val lockChannelKey: String?
        get() = null

    val lockTimeoutOverride: Duration?
        get() = null

    val shouldFailOnTimeoutOverride: Boolean?
        get() = null
}

interface ResultReturningLockAwareMessage<TResult : KBusResult> :
    ResultReturningMessage<TResult>, LockAwareMessage {
    fun busLockedFailure(failure: BusLockedFailure): TResult
}

interface LockingCommand<TResult : KBusResult> : ResultReturningLockAwareMessage<TResult> {
    override val shouldLockBus: Boolean
        get() = true
}

class BusLockToken(val heldKeys: Set<String> = emptySet()) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<BusLockToken>

    override val key: CoroutineContext.Key<*>
        get() = Key
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

    suspend fun lockBus(timeout: Duration): LockOutcome {
        val lockAcquired =
            withTimeoutOrNull(timeout) {
                mutex.lock()
                true
            }
        return if (lockAcquired == true) {
            LockOutcome.Success(this@KeyedLock)
        } else {
            if (this.forceUnlocked) {
                this.mutex.unlock()
                LockOutcome.RaceConditionFailure("Lock was force-unlocked while acquiring")
            }
            LockOutcome.TimeoutFailure("bus did not unlock in time")
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

sealed interface LockOutcome {
    data class Success(val activeLock: KeyedLock) : LockOutcome

    data class TimeoutFailure(val reason: String) : LockOutcome

    data class RaceConditionFailure(val reason: String) : LockOutcome
}

@OptIn(ExperimentalAtomicApi::class)
class BusLocker(
    private val threadSafeCache: Cache<String, KeyedLock> = ThreadSafeMapCache(),
    private val defaultTimeout: Duration = 5.seconds,
    private val defaultShouldFailOnTimeout: Boolean = false,
) : Middleware {
    companion object {
        private const val KEY_PREFIX = "bus-lock-"
        private const val GLOBAL_KEY_SUFFIX = "global-channel"
    }

    fun busIsLocked(channelKey: String? = null): Boolean =
        threadSafeCache.get(KEY_PREFIX + (channelKey ?: GLOBAL_KEY_SUFFIX))?.isLocked == true

    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        val lockAware = message as? LockAwareMessage
        val key = KEY_PREFIX + (lockAware?.lockChannelKey ?: GLOBAL_KEY_SUFFIX)
        if (currentCoroutineContext()[BusLockToken]?.heldKeys?.contains(key) == true) {
            return exitEarly(
                message,
                "Cannot handle message as message bus is locked by the same coroutine",
            )
        }

        val timeout = lockAware?.lockTimeoutOverride ?: defaultTimeout
        val shouldFailOnTimeout =
            lockAware?.shouldFailOnTimeoutOverride ?: defaultShouldFailOnTimeout

        return if (lockAware?.shouldLockBus == true) {
            processLockingMessage(message, key, timeout, shouldFailOnTimeout, nextMiddleware)
        } else {
            processNonLockingMessage(message, key, timeout, nextMiddleware)
        }
    }

    private suspend fun <TMessage : Message, TResult> processLockingMessage(
        message: TMessage,
        key: String,
        timeout: Duration,
        shouldFailOnTimeout: Boolean,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        val initialLock = getLockAndRegisterWaiter(message, key)
        var activeLock = initialLock

        try {
            return when (initialLock.lockBus(timeout)) {
                is LockOutcome.Success -> {
                    // TODO save lock?
                    activeLock = initialLock
                    dispatchWithLockContext(initialLock.key, message, nextMiddleware)
                }
                is LockOutcome.RaceConditionFailure ->
                    exitEarly(message, "Message aborted: Lock was hijacked while acquiring.")
                is LockOutcome.TimeoutFailure -> {
                    handleTimeout(shouldFailOnTimeout, initialLock, message, nextMiddleware)
                }
            }
        } finally {
            activeLock.unLockBus()
            deregisterWaiter(activeLock)
        }
    }

    private suspend fun <TMessage : Message, TResult> processNonLockingMessage(
        message: TMessage,
        key: String,
        timeout: Duration,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        val lock = getLockAndRegisterWaiter(message, key)

        try {
            return when {
                lock.waitFullTimeout(timeout) ->
                    exitEarly(message, "Timed out waiting for message bus to unlock")
                lock.forceUnlocked ->
                    exitEarly(message, "Message aborted: The lock was forcefully hijacked.")
                else -> dispatchWithLockContext(key, message, nextMiddleware)
            }
        } finally {
            deregisterWaiter(lock)
        }
    }

    private suspend fun <TMessage : Message, TResult> handleTimeout(
        shouldFailOnTimeout: Boolean,
        initialLock: KeyedLock,
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        if (shouldFailOnTimeout) {
            return exitEarly(message, "Message bus did not unlock in time")
        }

        initialLock.forceUnlock()
        val newLock = reacquireLock(initialLock)
        return if (newLock != null) {
            // TODO save new lock?
            //            activeLock = newLock
            dispatchWithLockContext(initialLock.key, message, nextMiddleware)
        } else {
            exitEarly(message, "Another process hijacked the lock")
        }
    }

    private suspend fun <TMessage : Message, TResult> dispatchWithLockContext(
        key: String,
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        val token = currentCoroutineContext()[BusLockToken]
        val newHeldKeys = (token?.heldKeys ?: emptySet()) + key

        return withContext(BusLockToken(newHeldKeys)) { nextMiddleware(message) }
    }

    private fun getLockAndRegisterWaiter(message: Message, key: String): KeyedLock {
        val timeout = (message as? LockAwareMessage)?.lockTimeoutOverride
        val lock = threadSafeCache.getOrPut(key) { KeyedLock(key, timeout, null) }

        lock.addWaiter()
        return lock
    }

    private suspend fun reacquireLock(oldLock: KeyedLock): KeyedLock? {
        val newLock = KeyedLock(oldLock.key, null, null)
        newLock.addWaiter()

        val swapSucceeded = threadSafeCache.replaceIfMatching(oldLock.key, oldLock, newLock)
        if (!swapSucceeded) {
            newLock.removeWaiter()
            return null
        }

        deregisterWaiter(oldLock)

        newLock.lockBus(1.milliseconds)
        return newLock
    }

    private fun deregisterWaiter(lock: KeyedLock) {
        val remainingWaiters = lock.removeWaiter()
        if (remainingWaiters == 0) {
            threadSafeCache.removeIfMatching(lock.key, lock)
        }
    }

    private fun <TMessage : Message, TResult> exitEarly(message: TMessage, error: String): TResult {
        return if (message is ResultReturningLockAwareMessage<*>) {
            @Suppress("UNCHECKED_CAST")
            message.busLockedFailure(BusLockedFailure(error)) as TResult
        } else {
            throw BusLockedException(error)
        }
    }
}

class BusLockedFailure(override val message: String) : FailureReason

class BusLockedException(override val message: String) : Exception(message)
