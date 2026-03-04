package com.jimbroze.kbus.core.middleware.middleware

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.contracts.common.ResultReturningMessage
import com.jimbroze.kbus.contracts.result.FailureReason
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareHandler
import com.jimbroze.kbus.core.middleware.middleware.lock.AtomicLock
import com.jimbroze.kbus.core.middleware.middleware.lock.LockOutcome
import com.jimbroze.kbus.core.middleware.middleware.lock.WaitOutcome
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// TODO fix for JS Browser
// TODO create queue?
interface LockAwareMessage {
    val shouldLockBus: Boolean
    val lockChannelKey: String?
        get() = null

    val lockTimeoutOverride: Duration?
        get() = null

    val ignoreLockOnTimeoutOverride: Boolean?
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

@Serializable
private data class BusLockData(
    val timeoutOverride: Duration?,
    val ignoreLockOnTimeoutOverride: Boolean?,
)

@OptIn(ExperimentalAtomicApi::class)
class BusLocker(
    private val atomicLock: AtomicLock,
    private val defaultTimeout: Duration,
    private val defaultLockExpiry: Duration,
    private val defaultIgnoreLockOnTimeout: Boolean = false,
) : Middleware {
    companion object {
        private const val KEY_PREFIX = "bus-lock-"
        private const val GLOBAL_KEY_SUFFIX = "global-channel"
    }

    suspend fun busIsLocked(channelKey: String? = null): Boolean =
        atomicLock.isLocked(key(channelKey))

    private fun key(channelKey: String?): String = KEY_PREFIX + (channelKey ?: GLOBAL_KEY_SUFFIX)

    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        val key = key((message as? LockAwareMessage)?.lockChannelKey)
        if (currentCoroutineContext()[BusLockToken]?.heldKeys?.contains(key) == true) {
            return exitEarly(
                message,
                "Cannot handle message as message bus is locked by the same coroutine",
            )
        }

        return if ((message as? LockAwareMessage)?.shouldLockBus == true) {
            processLockingMessage(message, key, nextMiddleware)
        } else {
            processNonLockingMessage(message, key, nextMiddleware)
        }
    }

    private suspend fun <TMessage : Message, TResult> processLockingMessage(
        message: TMessage,
        key: String,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        var lockToken: String? = null
        val lockingTimeout = (message as? LockAwareMessage)?.lockTimeoutOverride ?: defaultTimeout
        val ignoreLockOnTimeout =
            (message as? LockAwareMessage)?.ignoreLockOnTimeoutOverride
                ?: defaultIgnoreLockOnTimeout
        val metadata =
            Json.encodeToString(
                BusLockData(
                    (message as? LockAwareMessage)?.lockTimeoutOverride,
                    (message as? LockAwareMessage)?.ignoreLockOnTimeoutOverride,
                )
            )
        try {
            return when (
                val outcome =
                    atomicLock.acquireLock(key, defaultLockExpiry, lockingTimeout, metadata)
            ) {
                is LockOutcome.Success -> {
                    lockToken = outcome.lockToken
                    val dispatchWithLockContext =
                        dispatchWithLockContext(key, message, nextMiddleware)
                    dispatchWithLockContext
                }
                is LockOutcome.Timeout ->
                    handleTimeout(ignoreLockOnTimeout, key, message, nextMiddleware)
                is LockOutcome.ProviderError ->
                    exitEarly(message, "Message aborted: Lock was hijacked while acquiring.")
            }
        } finally {
            lockToken?.let { atomicLock.releaseLock(key, lockToken) }
        }
    }

    private suspend fun <TMessage : Message, TResult> processNonLockingMessage(
        message: TMessage,
        key: String,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        val lockData =
            atomicLock.getLockMetadata(key)?.let { Json.decodeFromString<BusLockData>(it) }

        val waitingTimeout =
            (message as? LockAwareMessage)?.lockTimeoutOverride
                ?: lockData?.timeoutOverride
                ?: defaultTimeout
        val ignoreLockOnTimeout =
            (message as? LockAwareMessage)?.ignoreLockOnTimeoutOverride
                ?: lockData?.ignoreLockOnTimeoutOverride
                ?: defaultIgnoreLockOnTimeout

        return when (atomicLock.waitForUnlock(key, waitingTimeout)) {
            is WaitOutcome.Unlocked -> dispatchWithLockContext(key, message, nextMiddleware)
            is WaitOutcome.Timeout ->
                handleTimeout(ignoreLockOnTimeout, key, message, nextMiddleware)
            is WaitOutcome.ProviderError ->
                exitEarly(message, "Message aborted: The lock was forcefully hijacked.")
        }
    }

    private suspend fun <TMessage : Message, TResult> handleTimeout(
        ignoreLockOnTimeout: Boolean,
        key: String,
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        return if (ignoreLockOnTimeout) {
            dispatchWithLockContext(key, message, nextMiddleware)
        } else {
            exitEarly(message, "Timed out waiting for message bus to unlock")
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
