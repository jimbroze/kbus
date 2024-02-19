package com.jimbroze.kbus.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class TestClock(private val scheduler: TestCoroutineScheduler) : Clock {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun now(): Instant = Instant.fromEpochMilliseconds(scheduler.currentTime)
}
