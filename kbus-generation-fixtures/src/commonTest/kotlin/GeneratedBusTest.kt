@file:OptIn(ExperimentalTime::class)

package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.core.boundedcontext.BoundedContextConfig
import com.jimbroze.kbus.core.middleware.AutoPublishIntegrationEvents
import com.jimbroze.kbus.core.registry.generation.domainSubscription
import com.jimbroze.kbus.core.uow.EmptyTransactionManager
import com.jimbroze.kbus.generated.CompileTimeLoadedMessageBus
import com.jimbroze.kbus.generated.generatedAutoPublishRegistrations
import com.jimbroze.kbus.generated.loaded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class GeneratedBusTest {
    @Test
    fun `executes a command through the generated bus`() = runTest {
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
        assertEquals("success", bus.execute(ExternalDependenciesCommandSub("")).getOrNull())
    }

    @Test
    fun `starts and stops the components the generated bus owns`() = runTest {
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
    fun `fetches a query through the generated bus`() = runTest {
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

    @Test
    fun `dispatches an event through the generated bus`() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                emptyList(),
                default =
                    BoundedContextConfig(
                        domainSubscriptions =
                            listOf(
                                domainSubscription(
                                    TestGeneratorEvent::class,
                                    TestGeneratorEventHandler::class.loaded,
                                )
                            )
                    ),
            )

        val handledBefore = TestGeneratorEventHandler.timesHandled
        bus.execute(TestEventPublishingCommand())
        assertEquals(handledBefore + 1, TestGeneratorEventHandler.timesHandled)
    }

    @Test
    fun `emits to observers an integration event a command published`() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                listOf(AutoPublishIntegrationEvents(generatedAutoPublishRegistrations)),
                appScope = backgroundScope,
            )

        val observed = async { bus.observe<ArrivalConfirmed>().first() }
        runCurrent()

        bus.execute(RecordArrival("item-9"))

        assertEquals("item-9", observed.await().itemId)
    }
}
