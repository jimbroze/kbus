@file:OptIn(ExperimentalTime::class)

package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.core.bus.IMessageBus
import com.jimbroze.kbus.core.bus.MessageBus
import com.jimbroze.kbus.core.infrastructure.lock.inMemoryAtomicLock
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.middleware.LockingMiddleware
import com.jimbroze.kbus.generated.AutoLoader
import com.jimbroze.kbus.testdoubles.AutoTickingClock
import com.test.external.ExternalEmpty
import com.test.external.ExternalNestedWithExternal
import com.test.external.ExternalNestedWithPrimitive
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope

class RecordingArrivalLog : ArrivalLog {
    val recordedItemIds = mutableListOf<String>()

    override suspend fun record(itemId: String) {
        recordedItemIds.add(itemId)
    }

    override suspend fun countFor(itemId: String): Int = recordedItemIds.count { it == itemId }
}

class Dependencies(private val instant: Instant, applicationScope: CoroutineScope) : AutoLoader() {
    override val lockingMiddleware by lazy {
        LockingMiddleware(
            { scope: CoroutineScope -> inMemoryAtomicLock(scope) },
            5.seconds,
            30.seconds,
        )
    }
    override val messageBus = MessageBus(appScope = applicationScope)

    override val anObject: AnObject = AnObject

    override val genericClassOfString: GenericClass<String> = GenericClass("a string")
    override val genericClassOfListOfString: GenericClass<List<String>> =
        GenericClass(listOf("a string in a list"))

    override val typeAliasStringOne = "hello, "
    override val typeAliasStringTwo = "hello again"

    override val clock: Clock = FixedClock(instant)
    val tickingClock: Clock = AutoTickingClock(instant)
    override val iMessageBus: IMessageBus = messageBus

    override val containsString = ContainsString("a string")
    override val containsFunction = ContainsFunction { a, b -> a + b }
    override val typeAliasStringCombiner: TypeAliasStringCombiner = { a, b -> a + b }
    override val externalEmpty = ExternalEmpty()
    override val externalInterface = externalEmpty
    override val externalNestedWithPrimitive = ExternalNestedWithPrimitive("A string")
    override val externalNestedWithExternal = ExternalNestedWithExternal(externalEmpty)

    val recordingArrivalLog = RecordingArrivalLog()
    override val arrivalLog: ArrivalLog = recordingArrivalLog

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
