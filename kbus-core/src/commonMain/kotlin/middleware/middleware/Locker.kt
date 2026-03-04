package com.jimbroze.kbus.core.middleware.middleware

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.contracts.common.ResultReturningMessage
import com.jimbroze.kbus.contracts.result.FailureReason
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareHandler
import com.jimbroze.kbus.core.middleware.middleware.lock.LockOutcome
import com.jimbroze.kbus.core.middleware.middleware.lock.LockProvider
import com.jimbroze.kbus.core.middleware.middleware.lock.WaitOutcome
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

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
class BusLocker(
    private val lockProvider: LockProvider,
    private val defaultTimeout: Duration = 5.seconds,
    private val defaultShouldFailOnTimeout: Boolean = false,
) : Middleware {
    companion object {
        private const val KEY_PREFIX = "bus-lock-"
        private const val GLOBAL_KEY_SUFFIX = "global-channel"
    }

    suspend fun busIsLocked(channelKey: String? = null): Boolean =
        lockProvider.isLocked(key(channelKey))

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
        var lockToken: String? = null
        try {
            // FIXME TTL?
            return when (val outcome = lockProvider.acquireLock(key, timeout, timeout)) {
                is LockOutcome.Success -> {
                    lockToken = outcome.lockToken
                    val dispatchWithLockContext =
                        dispatchWithLockContext(key, message, nextMiddleware)
                    dispatchWithLockContext
                }
                is LockOutcome.Timeout ->
                    handleTimeout(shouldFailOnTimeout, key, message, nextMiddleware)
                is LockOutcome.ProviderError ->
                    exitEarly(message, "Message aborted: Lock was hijacked while acquiring.")
            }
        } finally {
            lockToken?.let { lockProvider.releaseLock(key, lockToken) }
        }
    }

    private suspend fun <TMessage : Message, TResult> processNonLockingMessage(
        message: TMessage,
        key: String,
        timeout: Duration,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        return when (lockProvider.waitForUnlock(key, timeout)) {
            is WaitOutcome.Unlocked -> dispatchWithLockContext(key, message, nextMiddleware)
            is WaitOutcome.Timeout ->
                exitEarly(message, "Timed out waiting for message bus to unlock")
            is WaitOutcome.ProviderError ->
                exitEarly(message, "Message aborted: The lock was forcefully hijacked.")
        }
    }

    private suspend fun <TMessage : Message, TResult> handleTimeout(
        shouldFailOnTimeout: Boolean,
        key: String,
        message: TMessage,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        return if (shouldFailOnTimeout) {
            exitEarly(message, "Message bus did not unlock in time")
        } else {
            if (lockProvider.forceUnlock(key)) {
                dispatchWithLockContext(key, message, nextMiddleware)
            } else {
                exitEarly(message, "Message aborted: Lock was hijacked while handling timeout.")
            }
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
