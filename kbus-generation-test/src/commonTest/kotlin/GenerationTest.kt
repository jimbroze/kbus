package com.jimbroze.kbus.generation

import com.jimbroze.kbus.core.bus.BaseMessageBus
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.middleware.middleware.BusLocker
import com.jimbroze.kbus.core.uow.CommandDependencies
import com.jimbroze.kbus.core.uow.EmptyTransactionManager
import com.jimbroze.kbus.generated.AutoLoader
import com.jimbroze.kbus.generated.CompileTimeLoadedMessageBus
import com.jimbroze.kbus.generation.test.ContainsFunctions
import com.jimbroze.kbus.generation.test.ContainsString
import com.jimbroze.kbus.generation.test.FixedClock
import com.jimbroze.kbus.generation.test.GenericClass
import com.jimbroze.kbus.generation.test.OtherClassesCommandCommand
import com.jimbroze.kbus.generation.test.RequiresCommandDepsContainsInstant
import com.jimbroze.kbus.generation.test.TestGeneratorCommand
import com.jimbroze.kbus.generation.test.TestGeneratorQuery
import com.jimbroze.kbus.generation.test.TypeAliasStringCombiner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// TODO don't add any dependencies not in module???
// TODO pass source files to generator
// TODO example with object rather than class
// TODO containsInterface
// TODO containsFunction - remove typealias
// TODO 2nd TypeAlias with same resolved type
class Dependencies(instant: Instant) : AutoLoader() {
    override val clock: Clock by lazy { FixedClock(instant) }

    override val busLocker by lazy { BusLocker(clock) }

    override fun requiresCommandDepsContainsInstant(commandDependencies: CommandDependencies) =
        RequiresCommandDepsContainsInstant(
            this.requiresCommandDepsContainsClock(commandDependencies),
            clock.now(),
        )

    override val containsString by lazy { ContainsString("a string") }
    override val genericClassOfString: GenericClass<String> = GenericClass("a string")
    override val genericClassOfListOfString: GenericClass<List<String>> =
        GenericClass(listOf("a string in a list"))

    // TODO Transient examples
    //    override val clockFactoryHolder: ClockFactoryHolder
    //        get() = ClockFactoryHolder(clockFactory)

    override val messageBus by lazy { MessageBus() }
    override val baseMessageBus: BaseMessageBus = messageBus

    override val typeAliasString = "hello, "

    override val containsFunctions by lazy {
        ContainsFunctions(typeAliasStringCombiner, { a, b -> a + b })
    }
    override val typeAliasStringCombiner: TypeAliasStringCombiner = { a, b -> a + b }
}

class GenerationTest {
    @Test
    fun test_execute_executes_a_command() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z")),
                EmptyTransactionManager(),
                emptyList(),
            )

        val result = bus.execute(TestGeneratorCommand("The time is "))

        assertEquals("success", result.getOrNull())
    }

    @Test
    fun test_execute_executes_a_command_two() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z")),
                EmptyTransactionManager(),
                emptyList(),
            )

        val result = bus.execute(OtherClassesCommandCommand(null))

        assertEquals("success", result.getOrNull())
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
