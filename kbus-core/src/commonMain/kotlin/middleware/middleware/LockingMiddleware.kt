package com.jimbroze.kbus.core.middleware.middleware

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.contracts.middleware.BusLockedException
import com.jimbroze.kbus.contracts.middleware.BusLockedFailure
import com.jimbroze.kbus.contracts.middleware.LockAwareMessage
import com.jimbroze.kbus.contracts.middleware.ResultReturningLockAwareMessage
import com.jimbroze.kbus.core.infrastructure.lock.SignallingLock
import com.jimbroze.kbus.core.middleware.LifecycleAwareMiddleware
import com.jimbroze.kbus.core.middleware.MiddlewareContext
import com.jimbroze.kbus.core.middleware.MiddlewareHandler
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// TODO create queue?

@OptIn(ExperimentalAtomicApi::class, ExperimentalUuidApi::class)
class LockingMiddleware(
    private val lockFactory: (CoroutineScope) -> SignallingLock,
    private val defaultTimeout: Duration,
    private val defaultLockExpiry: Duration,
    private val defaultIgnoreLockOnTimeout: Boolean = false,
) : LifecycleAwareMiddleware {
    companion object {
        private const val KEY_PREFIX = "bus-lock-"
        private const val GLOBAL_KEY_SUFFIX = "global-channel"
        private val jsonConfig = Json { ignoreUnknownKeys = true }
    }

    private lateinit var atomicLock: SignallingLock

    override fun onStart(context: MiddlewareContext) {
        this.atomicLock = lockFactory(context.scope)
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

        val lockToken = Uuid.random().toString()

        return when (val outcome = acquireLock(key, lockToken, lockingTimeout, metadata)) {
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

        return when (val outcome = waitForUnlock(key, waitingTimeout)) {
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

    private suspend fun acquireLock(
        key: String,
        token: String,
        lockingTimeout: Duration,
        metadata: String,
    ): LockOutcome {
        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        return try {
            withTimeout(lockingTimeout) {
                while (true) {
                    if (atomicLock.tryAcquireLock(key, token, defaultLockExpiry, metadata)) {
                        return@withTimeout LockOutcome.Success(token)
                    }

                    var acquiredInSubscription = false
                    atomicLock.unlockEvents
                        .onSubscription {
                            if (
                                atomicLock.tryAcquireLock(key, token, defaultLockExpiry, metadata)
                            ) {
                                acquiredInSubscription = true
                                emit(key)
                            }
                        }
                        .first { it == key }

                    if (acquiredInSubscription) {
                        return@withTimeout LockOutcome.Success(token)
                    }
                }
                @Suppress("UNREACHABLE_CODE") error("Unreachable")
            }
        } catch (e: TimeoutCancellationException) {
            LockOutcome.Timeout
        } catch (e: Exception) {
            LockOutcome.ProviderError(e)
        }
    }

    private suspend fun waitForUnlock(key: String, waitingTimeout: Duration): WaitOutcome {
        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        return try {
            withTimeout(waitingTimeout) {
                if (!atomicLock.isLocked(key)) return@withTimeout WaitOutcome.Unlocked

                atomicLock.unlockEvents
                    .onSubscription { if (!atomicLock.isLocked(key)) emit(key) }
                    .first { it == key }

                WaitOutcome.Unlocked
            }
        } catch (e: TimeoutCancellationException) {
            WaitOutcome.Timeout
        } catch (e: Exception) {
            WaitOutcome.ProviderError(e)
        }
    }
}

@Serializable
private data class BusLockData(
    val timeoutOverride: Duration?,
    val ignoreLockOnTimeoutOverride: Boolean?,
)

private class BusLockToken(val heldKeys: Set<String> = emptySet()) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<BusLockToken>

    override val key: CoroutineContext.Key<*>
        get() = Key
}

private sealed interface LockOutcome {
    data class Success(val lockToken: String) : LockOutcome

    object Timeout : LockOutcome

    data class ProviderError(val exception: Throwable) : LockOutcome
}

private sealed interface WaitOutcome {
    object Unlocked : WaitOutcome

    object Timeout : WaitOutcome

    data class ProviderError(val exception: Throwable) : WaitOutcome
}
