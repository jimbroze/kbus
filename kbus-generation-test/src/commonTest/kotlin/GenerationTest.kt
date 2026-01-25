package com.jimbroze.kbus.generation

import com.jimbroze.kbus.core.bus.BaseMessageBus
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.middleware.middleware.BusLocker
import com.jimbroze.kbus.core.uow.CommandDependencies
import com.jimbroze.kbus.core.uow.EmptyTransactionManager
import com.jimbroze.kbus.generated.AutoLoader
import com.jimbroze.kbus.generated.CompileTimeLoadedMessageBus
import com.jimbroze.kbus.generation.test.AnObject
import com.jimbroze.kbus.generation.test.ContainsFunction
import com.jimbroze.kbus.generation.test.ContainsString
import com.jimbroze.kbus.generation.test.EagerSingletonExample
import com.jimbroze.kbus.generation.test.FixedClock
import com.jimbroze.kbus.generation.test.GenericClass
import com.jimbroze.kbus.generation.test.GenericClassCommand
import com.jimbroze.kbus.generation.test.InterfacesCommand
import com.jimbroze.kbus.generation.test.LazySingletonExample
import com.jimbroze.kbus.generation.test.LifeCycleTestCommand
import com.jimbroze.kbus.generation.test.NestedClassesCommand
import com.jimbroze.kbus.generation.test.NonClassTypesCommand
import com.jimbroze.kbus.generation.test.OtherClassesCommand
import com.jimbroze.kbus.generation.test.RequiresCommandDepsContainsPrimitive
import com.jimbroze.kbus.generation.test.TestGeneratorQuery
import com.jimbroze.kbus.generation.test.TransientExample
import com.jimbroze.kbus.generation.test.TypeAliasStringCombiner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// TODO don't add any dependencies not in module???
// TODO pass source files to generator
class Dependencies(private val instant: Instant) : AutoLoader() {
    override val clock: Clock by lazy { FixedClock(instant) }

    override val busLocker by lazy { BusLocker(clock) }

    override val containsString by lazy { ContainsString("a string") }
    override val genericClassOfString: GenericClass<String> = GenericClass("a string")
    override val genericClassOfListOfString: GenericClass<List<String>> =
        GenericClass(listOf("a string in a list"))

    override val messageBus by lazy { MessageBus() }
    override val baseMessageBus: BaseMessageBus = messageBus
    override val anObject: AnObject = AnObject

    override val typeAliasStringOne = "hello, "
    override val typeAliasStringTwo = "hello again"

    override val containsFunction by lazy { ContainsFunction { a, b -> a + b } }
    override val typeAliasStringCombiner: TypeAliasStringCombiner = { a, b -> a + b }

    override val transientExample: TransientExample
        get() = FixedClock(Clock.System.now())

    override val lazySingletonExample: LazySingletonExample by lazy {
        FixedClock(Clock.System.now())
    }

    override val eagerSingletonExample: EagerSingletonExample = FixedClock(Clock.System.now())

    override fun requiresCommandDepsContainsPrimitive(commandDependencies: CommandDependencies) =
        RequiresCommandDepsContainsPrimitive(
            this.requiresCommandDepsContainsInterface(commandDependencies),
            instant,
        )
}

class GenerationTest {
    @Test
    fun test_it_executes_commands() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z")),
                EmptyTransactionManager(),
                emptyList(),
            )

        assertEquals("success", bus.execute(NestedClassesCommand("")).getOrNull())
        assertEquals("success", bus.execute(OtherClassesCommand("")).getOrNull())
        assertEquals("success", bus.execute(GenericClassCommand("")).getOrNull())
        assertEquals("success", bus.execute(InterfacesCommand("")).getOrNull())
        assertEquals("success", bus.execute(NonClassTypesCommand("")).getOrNull())
    }

    @Test
    fun test_it_handles_lifecycles() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z")),
                EmptyTransactionManager(),
                emptyList(),
            )

        val firstResult = bus.execute(LifeCycleTestCommand(2)).getOrNull()
        assertNotNull(firstResult)

        val secondResult = bus.execute(LifeCycleTestCommand(2)).getOrNull()
        assertNotNull(secondResult)

        assertTrue(secondResult.transientTime > firstResult.transientTime)
        assertEquals(secondResult.lazySingletonTime, firstResult.lazySingletonTime)
        assertEquals(secondResult.eagerSingletonTime, firstResult.eagerSingletonTime)

        assertTrue(firstResult.lazySingletonTime > firstResult.eagerSingletonTime)
        assertTrue(secondResult.lazySingletonTime > secondResult.eagerSingletonTime)
    }

    @Test
    fun test_it_fetches_queries() = runTest {
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
