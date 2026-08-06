@file:OptIn(ExperimentalTime::class)

package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.core.infrastructure.inbox.InMemoryInboxStore
import com.jimbroze.kbus.core.infrastructure.outbox.InMemoryOutboxStore
import com.jimbroze.kbus.core.middleware.middleware.AutoPublishIntegrationEvents
import com.jimbroze.kbus.core.module.BoundedContextConfig
import com.jimbroze.kbus.core.module.inbox.BoundedContextInbox
import com.jimbroze.kbus.core.module.inbox.InboxAckPolicy
import com.jimbroze.kbus.core.module.inbox.InboxTuning
import com.jimbroze.kbus.core.uow.EmptyTransactionManager
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.generated.CompileTimeLoadedMessageBus
import com.jimbroze.kbus.generated.generatedAutoPublishRegistrations
import com.jimbroze.kbus.testdoubles.advanceVirtualTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class GeneratedBusOutboxTest {
    @Test
    fun `routes an integration event a command published through the outbox`() = runTest {
        val outboxStore = InMemoryOutboxStore()
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                listOf(AutoPublishIntegrationEvents(generatedAutoPublishRegistrations)),
                appScope = backgroundScope,
                outbox = OutboxConfig(store = outboxStore, pollInterval = 10.seconds),
                depot = BoundedContextConfig(integrationSubscriptions = depotSubscriptions),
            )
        bus.start()
        // Let the poller's immediate first (empty) pass settle into its long sleep.
        advanceVirtualTime(50)

        val handledBefore = ConfirmArrivalHandler.timesHandled
        bus.execute(RecordArrival("item-4"))
        advanceVirtualTime(150)

        assertEquals(handledBefore + 1, ConfirmArrivalHandler.timesHandled)
        assertTrue(
            outboxStore.fetchUnpublished(10).isEmpty(),
            "the outbox drained what the command published",
        )
    }

    /**
     * `opportunisticDispatch = false` so the assertion pins the inbox's own durable step: the
     * envelope is saved and left for the pump, not dispatched inline on the routing path.
     */
    @Test
    fun `saves what an inboxed context is routed before dispatching it`() = runTest {
        val inboxStore = InMemoryInboxStore()
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                listOf(AutoPublishIntegrationEvents(generatedAutoPublishRegistrations)),
                appScope = backgroundScope,
                outbox = OutboxConfig(store = InMemoryOutboxStore(), pollInterval = 10.seconds),
                inboxTuning =
                    InboxTuning(opportunisticDispatch = false, pollInterval = 50.milliseconds),
                depot =
                    BoundedContextConfig(
                        inbox = BoundedContextInbox(inboxStore, InboxAckPolicy.HonourEventStrategy),
                        integrationSubscriptions = depotSubscriptions,
                    ),
            )
        bus.start()
        advanceVirtualTime(50)

        val handledBefore = ConfirmArrivalHandler.timesHandled
        bus.execute(RecordArrival("item-5"))
        advanceVirtualTime(300)

        assertEquals(handledBefore + 1, ConfirmArrivalHandler.timesHandled)
        assertTrue(
            inboxStore.fetchPending(10).isEmpty(),
            "the inbox pump consumed and acked the envelope",
        )
    }

    @Test
    fun `refuses a command before start when an outbox is configured`() = runTest {
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                emptyList(),
                appScope = backgroundScope,
                outbox = OutboxConfig(store = InMemoryOutboxStore()),
            )

        assertFailsWith<IllegalStateException> { bus.execute(RecordArrival("item-6")) }
        assertFailsWith<IllegalStateException> { bus.fetch(ArrivalCount("item-6")) }
    }

    /**
     * `opportunisticDrain = false` leaves the poller as the only thing that delivers, so an entry
     * written straight to the store after `stop` isolates whether the background work is still
     * running.
     */
    @Test
    fun `polls the outbox no more once the bus is stopped`() = runTest {
        val outboxStore = InMemoryOutboxStore()
        val bus =
            CompileTimeLoadedMessageBus(
                Dependencies(Instant.parse("2024-02-23T19:01:09Z"), backgroundScope),
                EmptyTransactionManager(),
                listOf(AutoPublishIntegrationEvents(generatedAutoPublishRegistrations)),
                appScope = backgroundScope,
                outbox =
                    OutboxConfig(
                        store = outboxStore,
                        pollInterval = 50.milliseconds,
                        opportunisticDrain = false,
                    ),
                depot = BoundedContextConfig(integrationSubscriptions = depotSubscriptions),
            )
        bus.start()

        val handledBefore = ConfirmArrivalHandler.timesHandled
        bus.execute(RecordArrival("item-7"))
        advanceVirtualTime(200)
        assertEquals(handledBefore + 1, ConfirmArrivalHandler.timesHandled)

        bus.stop(1.seconds)
        val handledAtStop = ConfirmArrivalHandler.timesHandled
        outboxStore.save(listOf(EventEnvelope("after-stop", ArrivalConfirmed("item-8"))))
        advanceVirtualTime(500)

        assertEquals(
            handledAtStop,
            ConfirmArrivalHandler.timesHandled,
            "no poller is still draining after stop",
        )
        assertTrue(outboxStore.fetchUnpublished(10).isNotEmpty(), "the event is still durable")
    }
}
