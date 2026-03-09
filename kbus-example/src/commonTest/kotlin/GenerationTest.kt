package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.core.bus.BaseMessageBus
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.infrastructure.lock.inMemoryAtomicLock
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.middleware.middleware.LockingMiddleware
import com.jimbroze.kbus.core.uow.EmptyTransactionManager
import com.jimbroze.kbus.generated.AutoLoader
import com.jimbroze.kbus.generated.CompileTimeLoadedMessageBus
import com.jimbroze.kbus.testdoubles.AutoTickingClock
import com.test.external.ExternalEmpty
import com.test.external.ExternalNestedWithExternal
import com.test.external.ExternalNestedWithPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest

// TODO don't add any dependencies not in module???
class Dependencies(private val instant: Instant, private val applicationScope: CoroutineScope) :
    AutoLoader() {
    override val lockingMiddleware by lazy {
        LockingMiddleware(
            inMemoryAtomicLock(backgroundScope = applicationScope),
            5.seconds,
            30.seconds,
        )
    }
    override val messageBus = MessageBus()

    override val anObject: AnObject = AnObject

    override val genericClassOfString: GenericClass<String> = GenericClass("a string")
    override val genericClassOfListOfString: GenericClass<List<String>> =
        GenericClass(listOf("a string in a list"))

    override val typeAliasStringOne = "hello, "
    override val typeAliasStringTwo = "hello again"

    override val clock: Clock = FixedClock(instant)
    val tickingClock: Clock = AutoTickingClock(instant)
    override val baseMessageBus: BaseMessageBus = messageBus

    override val containsString = ContainsString("a string")
    override val containsFunction = ContainsFunction { a, b -> a + b }
    override val typeAliasStringCombiner: TypeAliasStringCombiner = { a, b -> a + b }
    override val externalEmpty = ExternalEmpty()
    override val externalInterface = externalEmpty
    override val externalNestedWithPrimitive = ExternalNestedWithPrimitive("A string")
    override val externalNestedWithExternal = ExternalNestedWithExternal(externalEmpty)

    override val transientExample: TransientExample
        get() = FixedClock(tickingClock.now())

    override val lazySingletonExample: LazySingletonExample by lazy {
        FixedClock(tickingClock.now())
    }
    override val eagerSingletonExample: EagerSingletonExample = FixedClock(tickingClock.now())

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
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                emptyList(),
            )

        assertEquals("success", bus.execute(NestedClassesCommand("")).getOrNull())
        assertEquals("success", bus.execute(OtherClassesCommand("")).getOrNull())
        assertEquals("success", bus.execute(GenericClassCommand("")).getOrNull())
        assertEquals("success", bus.execute(InterfacesCommand("")).getOrNull())
        assertEquals("success", bus.execute(NonClassTypesCommand("")).getOrNull())
        assertEquals("success", bus.execute(ExternalDependenciesCommand("")).getOrNull())
    }

    @Test
    fun test_it_handles_lifecycles() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                emptyList(),
            )

        val firstResult = bus.execute(LifeCycleTestCommand()).getOrNull()
        assertNotNull(firstResult)

        val secondResult = bus.execute(LifeCycleTestCommand()).getOrNull()
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
                Dependencies(instant, backgroundScope),
                EmptyTransactionManager(),
                emptyList(),
            )

        val result = bus.fetch(TestGeneratorQuery("The time is ", "now "))

        assertEquals("The time is now 2024-02-23T19:01:09Z", result.getOrNull())
    }
}
