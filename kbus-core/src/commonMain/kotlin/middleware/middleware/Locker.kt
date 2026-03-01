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
class KeyedLock(var timeoutOverride: Duration?, var shouldFailOnTimeout: Boolean?) {
    val mutex: Mutex = Mutex()
    val waiters = AtomicInt(0)
    @Volatile var isEvicted: Boolean = false
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
        threadSafeCache.get(KEY_PREFIX + (channelKey ?: GLOBAL_KEY_SUFFIX))?.mutex?.isLocked == true

    private sealed interface LockOutcome {
        data class Success(val activeLock: KeyedLock) : LockOutcome

        data class Failure(val reason: String) : LockOutcome
    }

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
            return when (
                val outcome = acquireOrForceLock(initialLock, key, timeout, shouldFailOnTimeout)
            ) {
                is LockOutcome.Failure -> exitEarly(message, outcome.reason)
                is LockOutcome.Success -> {
                    activeLock =
                        outcome.activeLock // Track the correct lock in case it was hijacked/forced
                    dispatchWithLockContext(key, message, nextMiddleware)
                }
            }
        } finally {
            deregisterWaiter(key, activeLock, unlockBus = true)
        }
    }

    private suspend fun acquireOrForceLock(
        initialLock: KeyedLock,
        key: String,
        timeout: Duration,
        shouldFailOnTimeout: Boolean,
    ): LockOutcome {
        val isMutexAcquired =
            withTimeoutOrNull(timeout) {
                initialLock.mutex.lock()
                true
            } ?: false

        return when {
            isMutexAcquired && initialLock.isEvicted -> {
                initialLock.mutex.unlock()
                LockOutcome.Failure("Message aborted: Lock was hijacked while acquiring.")
            }
            isMutexAcquired -> LockOutcome.Success(initialLock)
            shouldFailOnTimeout -> LockOutcome.Failure("Message bus did not unlock in time")
            else -> {
                val newLock = forceUnlockAndReacquire(initialLock, key)
                if (newLock != null) {
                    LockOutcome.Success(newLock)
                } else {
                    LockOutcome.Failure("Another process hijacked the lock")
                }
            }
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
            val isUnlockedInTime =
                withTimeoutOrNull(timeout) {
                    lock.mutex.withLock {} // Wait for availability without acquiring
                    true
                } ?: false

            return when {
                !isUnlockedInTime ->
                    exitEarly(message, "Timed out waiting for message bus to unlock")
                lock.isEvicted ->
                    exitEarly(message, "Message aborted: The lock was forcefully hijacked.")
                else -> dispatchWithLockContext(key, message, nextMiddleware)
            }
        } finally {
            deregisterWaiter(key, lock, unlockBus = false)
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
        val lock = threadSafeCache.getOrPut(key) { KeyedLock(timeout, null) }

        lock.waiters.incrementAndFetch()
        return lock
    }

    private suspend fun forceUnlockAndReacquire(oldLock: KeyedLock, key: String): KeyedLock? {
        oldLock.isEvicted = true

        val newLock = KeyedLock(null, null)
        newLock.waiters.incrementAndFetch()

        val swapSucceeded = threadSafeCache.replaceIfMatching(key, oldLock, newLock)
        if (!swapSucceeded) {
            newLock.waiters.decrementAndFetch()
            return null
        }

        deregisterWaiter(key, oldLock, unlockBus = false)

        newLock.mutex.lock()
        return newLock
    }

    private fun deregisterWaiter(key: String, lock: KeyedLock, unlockBus: Boolean) {
        if (unlockBus && lock.mutex.isLocked) {
            lock.mutex.unlock()
        }

        val remainingWaiters = lock.waiters.decrementAndFetch()
        if (remainingWaiters == 0) {
            threadSafeCache.removeIfMatching(key, lock)
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
