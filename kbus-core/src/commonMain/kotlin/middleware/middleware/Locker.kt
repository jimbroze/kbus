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
interface LockAwareMessage : Message {
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
        private val jsonConfig = Json { ignoreUnknownKeys = true }
    }

    suspend fun busIsLocked(channelKey: String? = null): Boolean =
        atomicLock.isLocked(key(channelKey))

    private fun key(channelKey: String?): String = KEY_PREFIX + (channelKey ?: GLOBAL_KEY_SUFFIX)

    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        val lockAware = message as? LockAwareMessage
        val key = key(lockAware?.lockChannelKey)

        if (currentCoroutineContext()[BusLockToken]?.heldKeys?.contains(key) == true) {
            return exitEarly(
                message,
                "Cannot handle message as message bus is locked by the same coroutine",
            )
        }

        return if (lockAware?.shouldLockBus == true) {
            processLockingMessage(message, key, nextMiddleware)
        } else {
            processNonLockingMessage(message, lockAware, key, nextMiddleware)
        }
    }

    private suspend fun <TMessage : LockAwareMessage, TResult> processLockingMessage(
        message: TMessage,
        key: String,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        val lockingTimeout = message.lockTimeoutOverride ?: defaultTimeout
        val ignoreLockOnTimeout = message.ignoreLockOnTimeoutOverride ?: defaultIgnoreLockOnTimeout

        val metadata =
            jsonConfig.encodeToString(
                BusLockData(
                    timeoutOverride = message.lockTimeoutOverride,
                    ignoreLockOnTimeoutOverride = message.ignoreLockOnTimeoutOverride,
                )
            )

        return when (
            val outcome = atomicLock.acquireLock(key, defaultLockExpiry, lockingTimeout, metadata)
        ) {
            is LockOutcome.Success -> {
                try {
                    dispatchWithLockContext(key, message, nextMiddleware)
                } finally {
                    atomicLock.releaseLock(key, outcome.lockToken)
                }
            }
            is LockOutcome.Timeout -> {
                handleTimeout(ignoreLockOnTimeout, key, message, nextMiddleware)
            }
            is LockOutcome.ProviderError -> {
                exitEarly(
                    message,
                    "Lock provider error during acquisition: ${outcome.exception.message}",
                )
            }
        }
    }

    private suspend fun <TMessage : Message, TResult> processNonLockingMessage(
        message: TMessage,
        lockAware: LockAwareMessage?,
        key: String,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        val lockData =
            atomicLock.getLockMetadata(key)?.let {
                try {
                    jsonConfig.decodeFromString<BusLockData>(it)
                } catch (e: Exception) {
                    null
                }
            }

        val waitingTimeout =
            lockAware?.lockTimeoutOverride ?: lockData?.timeoutOverride ?: defaultTimeout
        val ignoreLockOnTimeout =
            lockAware?.ignoreLockOnTimeoutOverride
                ?: lockData?.ignoreLockOnTimeoutOverride
                ?: defaultIgnoreLockOnTimeout

        return when (val outcome = atomicLock.waitForUnlock(key, waitingTimeout)) {
            is WaitOutcome.Unlocked -> {
                dispatchWithLockContext(key, message, nextMiddleware)
            }
            is WaitOutcome.Timeout -> {
                handleTimeout(ignoreLockOnTimeout, key, message, nextMiddleware)
            }
            is WaitOutcome.ProviderError -> {
                exitEarly(
                    message,
                    "Lock provider error while waiting: ${outcome.exception.message}",
                )
            }
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
