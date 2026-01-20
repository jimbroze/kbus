package com.jimbroze.kbus.generation

import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.middleware.middleware.BusLocker
import com.jimbroze.kbus.core.uow.CommandDependencies
import com.jimbroze.kbus.core.uow.EmptyTransactionManager
import com.jimbroze.kbus.generated.AutoLoader
import com.jimbroze.kbus.generated.CompileTimeLoadedMessageBus
import com.jimbroze.kbus.generation.test.ContainsFunctions
import com.jimbroze.kbus.generation.test.ContainsString
import com.jimbroze.kbus.generation.test.FixedClock
import com.jimbroze.kbus.generation.test.RequiresCommandDepsContainsInstant
import com.jimbroze.kbus.generation.test.TestDuplicateGeneratorCommand
import com.jimbroze.kbus.generation.test.TestGeneratorCommand
import com.jimbroze.kbus.generation.test.TestGeneratorQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class Dependencies(instant: Instant) : AutoLoader() {
    override val clock: Clock by lazy { FixedClock(instant) }

    override val busLocker by lazy { BusLocker(clock) }

    // TODO do we want this to auto-generate because of the default param? Create option for this
    override fun requiresCommandDepsContainsInstant(commandDependencies: CommandDependencies) =
        RequiresCommandDepsContainsInstant(
            this.requiresCommandDepsContainsClock(commandDependencies),
            clock.now(),
        )

    override val containsString by lazy { ContainsString("a string") }

    // Transient
    //    override val clockFactoryHolder: ClockFactoryHolder
    //        get() = ClockFactoryHolder(clockFactory)

    override val messageBus by lazy { MessageBus() }

    override val typeAliasString = "hello, "

    override val containsFunctions by lazy {
        ContainsFunctions({ a, b -> a + b }, { a, b -> a + b })
    }
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
