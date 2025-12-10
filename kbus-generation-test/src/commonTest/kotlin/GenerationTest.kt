package com.jimbroze.kbus.generation

import com.jimbroze.kbus.core.BusLocker
import com.jimbroze.kbus.core.CommandDependencies
import com.jimbroze.kbus.core.EmptyTransactionManager
import com.jimbroze.kbus.core.MessageBus
import com.jimbroze.kbus.generation.test.AbstractGeneratedDIContainer
import com.jimbroze.kbus.generation.test.CompileTimeLoadedMessageBus
import com.jimbroze.kbus.generation.test.ContainsInstant
import com.jimbroze.kbus.generation.test.ContainsString
import com.jimbroze.kbus.generation.test.FixedClock
import com.jimbroze.kbus.generation.test.StringCombinator
import com.jimbroze.kbus.generation.test.TestDuplicateGeneratorCommand
import com.jimbroze.kbus.generation.test.TestGeneratorCommand
import com.jimbroze.kbus.generation.test.TestGeneratorQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class Dependencies(instant: Instant) : AbstractGeneratedDIContainer() {
    override val clock: Clock by lazy { FixedClock(instant) }

    override val busLocker by lazy { BusLocker(clock) }

    // TODO do we want this to auto-generate because of the default param? Create option for this
    override fun containsInstant(commandDependencies: CommandDependencies) =
        ContainsInstant(this.clockFactory(commandDependencies), clock.now())

    override val containsString by lazy { ContainsString("a string") }

    // Transient
    //    override val clockFactoryHolder: ClockFactoryHolder
    //        get() = ClockFactoryHolder(clockFactory)

    override val messageBus by lazy { MessageBus() }

    // TODO don't allow primitive types?
    override val typeAliasString = "hello, "

    override val stringCombinator by lazy { StringCombinator({ a, b -> a + b }, { a, b -> a + b }) }
}

class GenerationTest {
    @Test
    fun test_execute_executes_a_command() = runTest {
        val instant = Instant.parse("2024-02-23T19:01:09Z")

        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(instant),
                EmptyTransactionManager(),
                emptyList(),
            )

        val result = bus.execute(TestGeneratorCommand("The time is "))

        assertEquals("The time is 2024-02-23T19:01:09Z", result.getOrNull())
    }

    @Test
    fun test_execute_executes_a_command_two() = runTest {
        val instant = Instant.parse("2024-02-23T19:01:09Z")

        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(instant),
                EmptyTransactionManager(),
                emptyList(),
            )

        val result = bus.execute(TestDuplicateGeneratorCommand(null))

        assertEquals("Null message hello, 2024-02-23T19:01:09Z[]", result.getOrNull())
    }

    @Test
    fun test_execute_executes_a_query() = runTest {
        val instant = Instant.parse("2024-02-23T19:01:09Z")

        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(instant),
                EmptyTransactionManager(),
                emptyList(),
            )

        val result = bus.fetch(TestGeneratorQuery("The time is ", "now "))

        assertEquals("The time is now 2024-02-23T19:01:09Z", result.getOrNull())
    }
}
