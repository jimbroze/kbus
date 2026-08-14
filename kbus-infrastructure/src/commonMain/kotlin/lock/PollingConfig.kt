package com.jimbroze.kbus.infrastructure.lock

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * How often, and for how long, a polling [SignallingLock] re-checks whether a lock was released.
 */
data class PollingConfig(
    val initialDelay: Duration = 100.milliseconds,
    val maxDelay: Duration = 2.seconds,
    val backoffFactor: Double = 2.0,
    val timeout: Duration = 30.seconds,
)
