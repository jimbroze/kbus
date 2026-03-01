package com.jimbroze.kbus.core.middleware.middleware

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.contracts.common.ResultReturningMessage
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.FailureReason
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareHandler
import com.jimbroze.kbus.core.middleware.middleware.cache.Cache
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// TODO change to lockaware and have boolean for locking
// TODO fix for JS Browser
// TODO allow providing key for locking per aggregate etc.
// TODO create lock interface and mutex impl
// TODO create queue?
interface LockingMessage {
    val lockTimeout: Duration?
        get() = null
}

interface ResultReturningLockingMessage<TResult : KBusResult> :
    ResultReturningMessage<TResult>, LockingMessage {
    fun busLockedFailure(failure: BusLockedFailure): TResult
}

interface AFailure : FailureReason

sealed interface TestingMessageFailure : MessageFailure {
    companion object {
        fun busLockedFailure(failure: BusLockedFailure): BusResult<Nothing, TestingMessageFailure> =
            BusResult.failure(BusLocked(failure))
    }

    data class A(override val reason: AFailure) : TestingMessageFailure

    data class BusLocked(override val reason: BusLockedFailure) : TestingMessageFailure
}

// TODO move to tests
class LockingMessageImpl(override val lockTimeout: Duration? = null) :
    LockingCommand<BusResult<Any, TestingMessageFailure>>,
    Command<BusResult<Any, TestingMessageFailure>>() {
    override val messageType: String = "locking"

    override fun busLockedFailure(
        failure: BusLockedFailure
    ): BusResult<Any, TestingMessageFailure> = TestingMessageFailure.busLockedFailure(failure)
}

// TODO remove interface
interface LockAdjustMessage {
    val lockTimeout: Duration?
    val shouldFailOnTimeout: Boolean?
}

interface LockingCommand<TResult : KBusResult> : ResultReturningLockingMessage<TResult>

interface LockingEvent : LockingMessage

class BusLockToken(val heldKeys: Set<String> = emptySet()) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<BusLockToken>

    override val key: CoroutineContext.Key<*>
        get() = Key
}

@OptIn(ExperimentalAtomicApi::class)
class KeyedLock(var timeoutOverride: Duration?, var shouldFailOnTimeout: Boolean?) {
    val mutex: Mutex = Mutex()
    val waiters = AtomicInt(0)
    @Volatile var isEvicted: Boolean = false
}

@OptIn(ExperimentalAtomicApi::class)
class BusLocker(
    private val clock: Clock,
    private val threadSafeCache: Cache<String, KeyedLock>,
    private val defaultTimeout: Duration = 5.seconds,
    private val defaultShouldFailOnTimeout: Boolean = false,
) : Middleware {
    val busLocked: Boolean
        get() = threadSafeCache.get("my-key")?.mutex?.isLocked == true

    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        val key = "my-key"
        if (isLockedByCurrentCoroutine(key)) {
            return exitEarly(
                message,
                "Cannot handle message as message bus is locked by the same coroutine",
            )
        }

        val timeout = (message as? LockAdjustMessage)?.lockTimeout ?: defaultTimeout
        val shouldFailOnTimeout =
            (message as? LockAdjustMessage)?.shouldFailOnTimeout ?: defaultShouldFailOnTimeout

        return if (message is LockingMessage) {
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
        var lock = acquireLock(message, key)

        try {
            val lockAcquired =
                withTimeoutOrNull(timeout) {
                    lock.mutex.lock()
                    true
                } ?: false

            if (!lockAcquired) {
                if (shouldFailOnTimeout) {
                    return exitEarly(message, "Message bus did not unlock in time")
                }

                lock =
                    forceUnlockAndReacquire(lock, key)
                        ?: return exitEarly(message, "Another process hijacked the lock")
            }

            if (lock.isEvicted) {
                if (lock.mutex.isLocked) lock.mutex.unlock()
                return exitEarly(message, "Message aborted: Lock was hijacked while acquiring.")
            }

            return handleMessage(key, nextMiddleware, message)
        } finally {
            releaseLock(key, lock, unlockBus = true)
        }
    }

    private suspend fun <TMessage : Message, TResult> processNonLockingMessage(
        message: TMessage,
        key: String,
        timeout: Duration,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        val lock = acquireLock(message, key)

        try {
            val unlocked =
                withTimeoutOrNull(timeout) {
                    lock.mutex.withLock {} // Wait for availability without acquiring
                    true
                } ?: false

            if (!unlocked) return exitEarly(message, "Timed out waiting for message bus to unlock")
            if (lock.isEvicted)
                return exitEarly(message, "Message aborted: The lock was forcefully hijacked.")

            return handleMessage(key, nextMiddleware, message)
        } finally {
            releaseLock(key, lock, unlockBus = false)
        }
    }

    private suspend fun <TMessage : Message, TResult> handleMessage(
        key: String,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
        message: TMessage,
    ): TResult {
        val token = currentCoroutineToken()
        val newHeldKeys = (token?.heldKeys ?: emptySet()) + key

        return withContext(BusLockToken(newHeldKeys)) { nextMiddleware(message) }
    }

    private fun acquireLock(message: Message, key: String): KeyedLock {
        val timeout = (message as? LockingMessage)?.lockTimeout
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

        // Safely drop our tracking of the old lock; the caller will track the new one.
        releaseLock(key, oldLock, unlockBus = false)

        newLock.mutex.lock()
        return newLock
    }

    private fun releaseLock(key: String, lock: KeyedLock, unlockBus: Boolean) {
        if (unlockBus && lock.mutex.isLocked) {
            lock.mutex.unlock()
        }

        val remainingWaiters = lock.waiters.decrementAndFetch()
        if (remainingWaiters == 0) {
            threadSafeCache.removeIfMatching(key, lock)
        }
    }

    private suspend fun isLockedByCurrentCoroutine(key: String): Boolean {
        val token = currentCoroutineToken()
        return token?.heldKeys?.contains(key) == true
    }

    private suspend fun currentCoroutineToken(): BusLockToken? =
        currentCoroutineContext()[BusLockToken]

    //    private suspend fun waitForUnlock(message: Message) {
    //        val timeout =
    //            (message as? LockAdjustMessage)?.lockTimeout
    //                ?: (threadSafeCache.expiryTime("my-key")?.let { it - clock.now() })
    //                ?: defaultTimeout
    //        // TODO override failOnTimeout
    //
    //        //        val timeout =
    //        //            timeoutOverride?.let { now.plus(timeoutOverride) }
    //        //                ?: expirableCache.expiryTime("my-key")
    //        //                ?: now.plus(defaultTimeout)
    //
    //        //        while (busLocked && clock.now() < timeout) {
    //        //            // FIXME why does yield() not work?
    //        //            delay(timeout)
    //        //        }
    //        delay(timeout)
    //        if (busLocked) {
    //            if (defaultShouldFailOnTimeout) {
    //                return exitEarly(message, "Message bus did not unlock in time")
    //            } else {
    //                unlockBus()
    //            }
    //        }
    //    }

    //    private fun lockBus(threadId: String, message: LockingMessage) {
    //        val timeToLive = message.lockTimeout ?: defaultTimeout
    //        threadSafeCache.putExpiring("my-key", threadId, timeToLive)
    //    }
    //
    //    private fun unlockBus() {
    //        threadSafeCache.remove("my-key")
    //    }

    private fun <TMessage : Message, TResult> exitEarly(message: TMessage, error: String): TResult {
        return if (message is ResultReturningLockingMessage<*>) {
            @Suppress("UNCHECKED_CAST")
            message.busLockedFailure(BusLockedFailure(error)) as TResult
        } else {
            throw BusLockedException(error)
        }
    }

    //    private suspend fun getCoroutineId(): String {
    //        return currentCoroutineContext()[Job]?.toString() ?: ""
    //    }
}

class BusLockedFailure(override val message: String) : FailureReason

class BusLockedException(override val message: String) : Exception(message)
