package com.jimbroze.kbus.generation

import com.jimbroze.kbus.core.CompileTimeGeneratedLoader
import com.jimbroze.kbus.core.CompileTimeLoadedMessageBus
import com.jimbroze.kbus.core.GeneratedDependencies
import com.jimbroze.kbus.generation.test.TestGeneratorCommandLoaded
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

import kotlinx.datetime.Instant

class FixedClock(private var fixedInstant: Instant): Clock {
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
            override fun getClock(): Clock = clock
        }

        val bus = CompileTimeLoadedMessageBus(
            emptyList(),
            CompileTimeGeneratedLoader(Dependencies())
        )

        val result = bus.execute(TestGeneratorCommandLoaded("The time is "))

        assertEquals("The time is 2024-02-23T19:01:09Z", result)
    }
}
