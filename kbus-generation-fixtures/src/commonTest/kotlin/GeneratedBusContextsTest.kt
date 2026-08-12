@file:OptIn(ExperimentalTime::class)

package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.contracts.common.MissingHandlerException
import com.jimbroze.kbus.core.boundedcontext.BoundedContextConfig
import com.jimbroze.kbus.core.middleware.AutoPublishIntegrationEvents
import com.jimbroze.kbus.core.registry.generation.domainSubscription
import com.jimbroze.kbus.core.registry.generation.integrationSubscription
import com.jimbroze.kbus.core.uow.EmptyTransactionManager
import com.jimbroze.kbus.generated.CompileTimeLoadedMessageBus
import com.jimbroze.kbus.generated.generatedAutoPublishRegistrations
import com.jimbroze.kbus.generated.loaded
import com.jimbroze.kbus.testdoubles.advanceVirtualTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class GeneratedBusContextsTest {
    @Test
    fun `puts handlers declaring no bounded context identity in the default context`() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                listOf(AutoPublishIntegrationEvents(generatedAutoPublishRegistrations)),
                default = BoundedContextConfig(integrationSubscriptions = defaultSubscriptions),
            )

        val handledBefore = TestShipmentIntegrationHandler.timesHandled
        bus.execute(TestShipmentCommand())
        assertEquals(handledBefore + 1, TestShipmentIntegrationHandler.timesHandled)
    }

    @Test
    fun `lets a handler call a sibling command in its own context through the typed executor`() =
        runTest {
            val dependencies = Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope)
            val bus =
                CompileTimeLoadedMessageBus(
                    dependencies,
                    EmptyTransactionManager(),
                    emptyList(),
                    appScope = backgroundScope,
                )

            val result = bus.execute(RecordArrivalAndRestock("item-1"))

            assertEquals("item-1", result.getOrNull())
            assertEquals(listOf("item-1"), dependencies.recordingArrivalLog.recordedItemIds)
        }

    @Test
    fun `delivers a submodule command's domain event to its own context's domain handler`() =
        runTest {
            val bus =
                CompileTimeLoadedMessageBus(
                    Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                    EmptyTransactionManager(),
                    emptyList(),
                    appScope = backgroundScope,
                    depot =
                        BoundedContextConfig(
                            domainSubscriptions =
                                listOf(
                                    domainSubscription(
                                        ArrivalRecorded::class,
                                        AuditArrivalHandler::class.loaded,
                                    )
                                )
                        ),
                )

            AuditArrivalHandler.auditedItemIds.clear()
            bus.execute(RecordArrival("item-2"))
            // The handler dispatches after primary work, so it outlives the command's return.
            advanceVirtualTime(100)

            assertEquals(listOf("item-2"), AuditArrivalHandler.auditedItemIds)
        }

    @Test
    fun `builds a domain handler with the publisher it publishes through`() = runTest {
        val handledBefore = TestShipmentIntegrationHandler.timesHandled
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                emptyList(),
                appScope = backgroundScope,
                default =
                    BoundedContextConfig(
                        domainSubscriptions =
                            listOf(
                                domainSubscription(
                                    TestGeneratorEvent::class,
                                    TestPublishingGeneratorEventHandler::class.loaded,
                                )
                            ),
                        integrationSubscriptions =
                            listOf(
                                integrationSubscription(
                                    TestShipmentIntegration::class,
                                    TestShipmentIntegrationHandler::class.loaded,
                                )
                            ),
                    ),
            )

        bus.execute(TestEventPublishingCommand())
        advanceVirtualTime(100)

        assertEquals(handledBefore + 1, TestShipmentIntegrationHandler.timesHandled)
    }

    @Test
    fun `fetches a query from each context that owns one`() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                emptyList(),
                appScope = backgroundScope,
            )

        bus.execute(RecordArrival("item-3"))

        assertEquals(1, bus.fetch(ArrivalCount("item-3")).getOrNull())
        assertNotNull(bus.fetch(TestGeneratorQuery("a", "b")).getOrNull())
    }

    @Test
    fun `refuses to nest a command another context owns`() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                emptyList(),
                appScope = backgroundScope,
            )

        assertFailsWith<MissingHandlerException> { bus.execute(NestForeignCommand()) }
    }

    @Test
    fun `dispatches to each context only its own integration handlers`() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                listOf(AutoPublishIntegrationEvents(generatedAutoPublishRegistrations)),
                appScope = backgroundScope,
                default = BoundedContextConfig(integrationSubscriptions = defaultSubscriptions),
                depot = BoundedContextConfig(integrationSubscriptions = depotSubscriptions),
            )

        val defaultHandledBefore = TestShipmentIntegrationHandler.timesHandled
        val depotHandledBefore = ConfirmArrivalHandler.timesHandled

        bus.execute(RecordArrival("item-10"))
        // Integration dispatch is fire-and-forget, so give it a moment to land.
        advanceVirtualTime(100)

        // Only the depot context has a handler for ArrivalConfirmed; the default one is untouched.
        assertEquals(depotHandledBefore + 1, ConfirmArrivalHandler.timesHandled)
        assertEquals(defaultHandledBefore, TestShipmentIntegrationHandler.timesHandled)
    }
}
