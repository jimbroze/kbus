package com.jimbroze.kbus.core

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

class TestClock(private val scheduler: TestCoroutineScheduler) : Clock {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun now(): Instant = Instant.fromEpochMilliseconds(scheduler.currentTime)
}
