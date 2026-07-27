package com.jimbroze.kbus.testdoubles

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

/**
 * Advances the virtual clock by [millis] and then runs whatever that landed on, so background work
 * a bus launched — a poller tick, an inbox pump, a detached handler — has actually run by the time
 * this returns. `advanceUntilIdle` cannot be used in its place: a poller re-arms itself forever, so
 * the scheduler never reaches idle.
 */
fun TestScope.advanceVirtualTime(millis: Long) {
    advanceTimeBy(millis.milliseconds)
    runCurrent()
}
