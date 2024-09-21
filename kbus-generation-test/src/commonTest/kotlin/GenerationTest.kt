package com.jimbroze.kbus.generation

import com.jimbroze.kbus.core.*
import com.jimbroze.kbus.generation.test.TestGeneratorCommandLoaded
import com.jimbroze.kbus.generation.test.TestGeneratorQueryLoaded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FixedClock(private var fixedInstant: Instant) : Clock {
    override fun now(): Instant = fixedInstant

    fun travelTo(instant: Instant) {
        fixedInstant = instant
    }
}

private fun Clock.Companion.Fixed(fixedInstant: Instant): Clock = FixedClock(fixedInstant)

class GenerationTest {
    @Test
    fun test_execute_executes_a_command() = runTest {
        val clock = FixedClock(Instant.parse("2024-02-23T19:01:09Z"))
        class Dependencies : GeneratedDependencies {
            override fun getLocker() = BusLocker(clock)

            override fun getClock(): Clock = clock

            override fun getBus() = MessageBus()
        }

        val bus =
            CompileTimeLoadedMessageBus(emptyList(), CompileTimeGeneratedLoader(Dependencies()))

        val result = bus.execute(TestGeneratorCommandLoaded("The time is "))

        assertEquals("The time is 2024-02-23T19:01:09Z", result.getOrNull())
    }

    @Test
    fun test_execute_executes_a_query() = runTest {
        val clock = FixedClock(Instant.parse("2024-02-23T19:01:09Z"))
        class Dependencies : GeneratedDependencies {
            override fun getLocker() = BusLocker(clock)

            override fun getClock(): Clock = clock

            override fun getBus() = MessageBus()
        }

        val bus =
            CompileTimeLoadedMessageBus(emptyList(), CompileTimeGeneratedLoader(Dependencies()))

        val result = bus.execute(TestGeneratorQueryLoaded("The time is "))

        assertEquals("The time is 2024-02-23T19:01:09Z", result.getOrNull())
    }
}
