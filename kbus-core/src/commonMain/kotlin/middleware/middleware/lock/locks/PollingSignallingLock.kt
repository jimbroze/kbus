package com.jimbroze.kbus.core.middleware.middleware.lock.locks

import com.jimbroze.kbus.core.middleware.middleware.lock.AtomicLock
import com.jimbroze.kbus.core.middleware.middleware.lock.SignallingLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Configuration for the polling behavior of [PollingSignallingLock]. */
data class PollingConfig(
    val initialDelay: Duration = 100.milliseconds,
    val maxDelay: Duration = 2.seconds,
    val backoffFactor: Double = 2.0,
    val timeout: Duration = 30.seconds,
)

class PollingSignallingLock(
    private val delegate: AtomicLock,
    private val backgroundScope: CoroutineScope,
    private val config: PollingConfig = PollingConfig(),
) : SignallingLock, AtomicLock by delegate {
    // Using a buffered flow dropping the oldest events prevents suspension deadlocks
    // if subscribers are slow. Missed signals are safely caught by the polling fallback.
    private val _unlockEvents =
        MutableSharedFlow<String>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val unlockEvents: SharedFlow<String> = _unlockEvents

    override suspend fun publishUnlock(key: String) {
        _unlockEvents.emit(key)
    }

    override suspend fun releaseLock(key: String, lockToken: String): Boolean {
        val released = delegate.releaseLock(key, lockToken)
        if (released) {
            publishUnlock(key)
        }
        return released
    }

    override suspend fun tryAcquireLock(
        key: String,
        token: String,
        ttl: Duration,
        metadata: String?,
    ): Boolean {
        if (delegate.tryAcquireLock(key, token, ttl, metadata)) {
            return true
        }

        backgroundScope.launch { watchForUnlock(key) }
        return false
    }

    private suspend fun watchForUnlock(key: String) {
        var currentDelay = config.initialDelay

        withTimeoutOrNull(config.timeout) {
            while (true) {
                delay(currentDelay)

                if (!delegate.isLocked(key)) {
                    publishUnlock(key)
                    break
                }

                val nextMillis = (currentDelay.inWholeMilliseconds * config.backoffFactor).toLong()
                currentDelay = nextMillis.milliseconds.coerceAtMost(config.maxDelay)
            }
        }
    }
}
