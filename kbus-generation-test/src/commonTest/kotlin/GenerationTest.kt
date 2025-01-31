package com.jimbroze.kbus.generation

import com.jimbroze.kbus.core.BusLocker
import com.jimbroze.kbus.core.CompileTimeLoadedMessageBus
import com.jimbroze.kbus.core.GeneratedDIContainer
import com.jimbroze.kbus.core.MessageBus
import com.jimbroze.kbus.generation.test.ClockFactoryHolder
import com.jimbroze.kbus.generation.test.FixedClock
import com.jimbroze.kbus.generation.test.StringCombinator
import com.jimbroze.kbus.generation.test.TestGeneratorCommandLoaded
import com.jimbroze.kbus.generation.test.TestGeneratorQueryLoaded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class Dependencies(instant: Instant) : GeneratedDIContainer() {
    val clock = FixedClock(instant)

    override fun getBusLocker() = BusLocker(clock)

    override fun getClockFactoryHolder(): ClockFactoryHolder {
        return ClockFactoryHolder(getClockFactory())
    }

    override fun getClock(): Clock = clock

    override fun getMessageBus() = MessageBus()

    override fun getTypeAliasString() = "hello, "

    override fun getStringCombinator() = StringCombinator({ a, b -> a + b }, { a, b -> a + b })
}

class GenerationTest {
    @Test
    fun test_execute_executes_a_command() = runTest {
        val instant = Instant.parse("2024-02-23T19:01:09Z")

        val bus = CompileTimeLoadedMessageBus(emptyList(), Dependencies(instant))

        val result = bus.execute(TestGeneratorCommandLoaded("The time is "))

        assertEquals("The time is 2024-02-23T19:01:09Z", result.getOrNull())
    }

    @Test
    fun test_execute_executes_a_query() = runTest {
        val instant = Instant.parse("2024-02-23T19:01:09Z")

        val bus = CompileTimeLoadedMessageBus(emptyList(), Dependencies(instant))

        val result = bus.execute(TestGeneratorQueryLoaded("The time is "))

        assertEquals("The time is 2024-02-23T19:01:09Z", result.getOrNull())
    }
}
