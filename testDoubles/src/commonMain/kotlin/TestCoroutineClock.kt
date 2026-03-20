@file:OptIn(ExperimentalTime::class)

package com.jimbroze.kbus.testdoubles

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

class TestCoroutineClock(private val scheduler: TestCoroutineScheduler) : Clock {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun now(): Instant = Instant.fromEpochMilliseconds(scheduler.currentTime)
}
