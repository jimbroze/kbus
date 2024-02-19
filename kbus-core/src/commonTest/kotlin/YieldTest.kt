package com.jimbroze.kbus.core

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

class YieldTest {
    @Volatile
    var locked: Boolean = false

    @Test
    fun testWhileYield() = runTest {
        var x = 0
        val job1 = launch {
            while (true) {
                yield()
                ++x
            }
        }
        var y = 0
        val job2 = launch {
            while (true) {
                yield()
                ++y
            }
        }
        val job3 = launch {
            while(x < 100 && y < 100) {
                yield()
            }
            job1.cancelAndJoin()
            job2.cancelAndJoin()
        }
        job3.join()
        assertFalse(job1.isActive)
        assertFalse(job2.isActive)
        assertFalse(job3.isActive)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun test_delay_yields_coroutine() = runTest {
        val clock = Clock.System
//        val clock = TestClock(testScheduler)
        val timeSource = TimeSource.Monotonic
//        val timeSource = clock.asTimeSource()
//        val timeSource = TestClock(testScheduler).asTimeSource()

        locked = true
        val job1 = async {
//            locked = true
            delay((500).toLong())
            locked = false

            timeSource.markNow()
        }

        val job2 = async {

            val timeout = clock.now().plus(5, DateTimeUnit.SECOND)
            while (locked && clock.now() <= timeout) {
                delay(1)
            }

            timeSource.markNow()
        }

        val afterSleep = job1.await()
        val afterUnlock = job2.await()

        print(afterSleep)
        print(afterUnlock)
        print(afterSleep - afterUnlock)

        assertTrue(afterSleep < afterUnlock)
    }

//    @Test
//    fun test_yield_yields_coroutine() = runTest {
//        val clock = Clock.System
//        val timeSource = TimeSource.Monotonic
//
//        val job1 = async {
//            locked = true
//            delay((500).toLong())
//            locked = false
//
//            timeSource.markNow()
//        }
//
//        val job2 = async {
//
//            val timeout = clock.now().plus(5, DateTimeUnit.SECOND)
//            while (locked && clock.now() <= timeout) {
//                yield()
//            }
//
//            timeSource.markNow()
//        }
//
//        val afterSleep = job1.await()
//        val afterUnlock = job2.await()
//
//        assertTrue(afterSleep < afterUnlock)
//    }
}
