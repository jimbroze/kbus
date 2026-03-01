package com.jimbroze.kbus.generation.test.fixtures

import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class AutoTickingClock(private var current: Instant) : Clock {
    override fun now(): Instant {
        val timeToReturn = current
        current += 1.milliseconds
        return timeToReturn
    }
}
