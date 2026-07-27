package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.ErrorStrategy
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventHandler
import com.jimbroze.kbus.contracts.outbox.OutboxStore
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.core.fixtures.RecordingInboxStore
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.module.BoundedContextId
import com.jimbroze.kbus.core.module.inbox.InboxAckPolicy
import com.jimbroze.kbus.core.module.inbox.InboxConfig
import com.jimbroze.kbus.core.registry.HandlerLocator
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.uow.OutboxConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/**
 * [ErrorStrategy.FailFast] so a throwing handler surfaces synchronously, matching
 * [MessageBusMultiContextTest].
 */
private class InboxAlphaEvent(val name: String) : IntegrationEvent() {
    override val errorStrategy = ErrorStrategy.FailFast
}

private class PublishInboxAlphaCommand(val name: String) :
    Command<BusResult<Unit, MessageFailure>>()

private class PublishInboxAlphaCommandHandler :
    CommandHandler<PublishInboxAlphaCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(
        message: PublishInboxAlphaCommand
    ): BusResult<Unit, MessageFailure> {
        publish(InboxAlphaEvent(message.name))
        return BusResult.success(Unit)
    }
}

private class RecordingInboxAlphaHandler(
    private val received: MutableList<String>,
    private val label: String,
) : IntegrationEventHandler<InboxAlphaEvent> {
    override suspend fun handle(message: InboxAlphaEvent) {
        received.add("$label:${message.name}")
    }
}

private class SecondRecordingInboxAlphaHandler(
    private val received: MutableList<String>,
    private val label: String,
) : IntegrationEventHandler<InboxAlphaEvent> {
    override suspend fun handle(message: InboxAlphaEvent) {
        received.add("$label:${message.name}")
    }
}

private class ThrowingInboxAlphaHandler(private val attempts: MutableList<String>) :
    IntegrationEventHandler<InboxAlphaEvent> {
    override suspend fun handle(message: InboxAlphaEvent) {
        attempts.add(message.name)
        error("context handler failed")
    }
}

/**
 * An [OutboxStore] whose [markPublished] never acks, so the poller re-routes the same entry
 * forever.
 */
private class NonAckingOutboxStore : OutboxStore {
    private val entries = mutableListOf<EventEnvelope>()
    val fetchCount = mutableListOf<Int>()

    override suspend fun save(entries: List<EventEnvelope>) {
        this.entries.addAll(entries)
    }

    override suspend fun fetchUnpublished(limit: Int): List<EventEnvelope> {
        fetchCount.add(limit)
        return entries.take(limit)
    }

    override suspend fun markPublished(ids: List<String>) {
        // Never acks: simulates a producer that can't mark entries published, so every poll
        // re-routes the same envelope.
    }
}

/**
 * Default settings: [com.jimbroze.kbus.contracts.messages.event.Concurrency.Concurrent] +
 * FireAndForget.
 */
private class GatedInboxEvent(val name: String) : IntegrationEvent()

private class PublishGatedInboxCommand(val name: String) :
    Command<BusResult<Unit, MessageFailure>>()

private class PublishGatedInboxCommandHandler :
    CommandHandler<PublishGatedInboxCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(
        message: PublishGatedInboxCommand
    ): BusResult<Unit, MessageFailure> {
        publish(GatedInboxEvent(message.name))
        return BusResult.success(Unit)
    }
}

/**
 * Suspends until [gate] completes, so a test can observe pre-completion ack state
 * deterministically.
 */
private class GatedInboxHandler(
    private val received: MutableList<String>,
    private val gate: CompletableDeferred<Unit>,
) : IntegrationEventHandler<GatedInboxEvent> {
    override suspend fun handle(message: GatedInboxEvent) {
        gate.await()
        received.add(message.name)
    }
}

/** Suspends until [gate] completes, then always fails — for exercising ack-policy retries. */
private class ThrowingGatedInboxHandler(
    private val attempts: MutableList<String>,
    private val gate: CompletableDeferred<Unit>,
) : IntegrationEventHandler<GatedInboxEvent> {
    override suspend fun handle(message: GatedInboxEvent) {
        gate.await()
        attempts.add(message.name)
        error("handler failed")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MessageBusInboxTest {
    private suspend fun realDelay(millis: Long) =
        withContext(Dispatchers.Default) { delay(millis.milliseconds) }

    private fun registerPublishingCommand(stores: HandlerFactoryStoreCollection) {
        stores.commandStore.registerHandlers(
            PublishInboxAlphaCommand::class,
            listOf(
                CommandHandlerFactory(PublishInboxAlphaCommandHandler::class) {
                    PublishInboxAlphaCommandHandler()
                }
            ),
        )
    }

    private fun registerAlphaHandlerIn(
        stores: HandlerFactoryStoreCollection,
        locator: PersistingHandlerLocator,
        received: MutableList<String>,
        label: String,
    ) {
        stores.eventStore.registerHandlers(
            InboxAlphaEvent::class,
            listOf(
                EventHandlerFactory(RecordingInboxAlphaHandler::class) {
                    RecordingInboxAlphaHandler(received, label)
                }
            ),
        )
        locator.integrationEventMapper.addEventHandlers(
            InboxAlphaEvent::class,
            listOf(RecordingInboxAlphaHandler::class),
        )
    }

    private fun registerSecondAlphaHandlerIn(
        stores: HandlerFactoryStoreCollection,
        locator: PersistingHandlerLocator,
        received: MutableList<String>,
        label: String,
    ) {
        stores.eventStore.registerHandlers(
            InboxAlphaEvent::class,
            listOf(
                EventHandlerFactory(SecondRecordingInboxAlphaHandler::class) {
                    SecondRecordingInboxAlphaHandler(received, label)
                }
            ),
        )
        locator.integrationEventMapper.addEventHandlers(
            InboxAlphaEvent::class,
            listOf(SecondRecordingInboxAlphaHandler::class),
        )
    }

    private fun registerThrowingHandlerIn(
        stores: HandlerFactoryStoreCollection,
        locator: PersistingHandlerLocator,
        attempts: MutableList<String>,
    ) {
        stores.eventStore.registerHandlers(
            InboxAlphaEvent::class,
            listOf(
                EventHandlerFactory(ThrowingInboxAlphaHandler::class) {
                    ThrowingInboxAlphaHandler(attempts)
                }
            ),
        )
        locator.integrationEventMapper.addEventHandlers(
            InboxAlphaEvent::class,
            listOf(ThrowingInboxAlphaHandler::class),
        )
    }

    @Test
    fun aFailingContextIsRetriedAlone_theOutboxEntryIsAckedAndHealthyContextsDispatchOnce() =
        runTest {
            val outboxStore = RecordingOutboxStore()
            val stores = HandlerFactoryStoreCollection()
            val busLocator = PersistingHandlerLocator(stores)
            registerPublishingCommand(stores)

            val healthyReceived = mutableListOf<String>()
            val failedAttempts = mutableListOf<String>()
            val healthyLocator = PersistingHandlerLocator(stores)
            val failingLocator = PersistingHandlerLocator(stores)
            registerAlphaHandlerIn(stores, healthyLocator, healthyReceived, "healthy")
            registerThrowingHandlerIn(stores, failingLocator, failedAttempts)

            val bus =
                MessageBus(
                    busLocator,
                    rootScope = backgroundScope,
                    outbox = OutboxConfig(store = outboxStore),
                    contexts =
                        mapOf(
                            BoundedContextId("healthy") to healthyLocator as HandlerLocator,
                            BoundedContextId("failing") to failingLocator,
                        ),
                    inbox =
                        InboxConfig(
                            ackPolicy = InboxAckPolicy.HonourEventStrategy,
                            stores =
                                mapOf(
                                    BoundedContextId("healthy") to RecordingInboxStore(),
                                    BoundedContextId("failing") to RecordingInboxStore(),
                                ),
                            pollInterval = 100.milliseconds,
                        ),
                )
            bus.start()

            bus.execute(PublishInboxAlphaCommand("event"))
            realDelay(600)

            assertEquals(1, outboxStore.markedPublished.size)
            assertEquals(listOf("healthy:event"), healthyReceived)
            assertTrue(
                failedAttempts.size >= 2,
                "expected the inbox pump to retry: $failedAttempts",
            )
        }

    @Test
    fun aFailingContextsEnvelopeStaysPendingInItsOwnInboxOnly() = runTest {
        val outboxStore = RecordingOutboxStore()
        val stores = HandlerFactoryStoreCollection()
        val busLocator = PersistingHandlerLocator(stores)
        registerPublishingCommand(stores)

        val healthyLocator = PersistingHandlerLocator(stores)
        val failingLocator = PersistingHandlerLocator(stores)
        registerAlphaHandlerIn(stores, healthyLocator, mutableListOf(), "healthy")
        registerThrowingHandlerIn(stores, failingLocator, mutableListOf())

        val healthyStore = RecordingInboxStore()
        val failingStore = RecordingInboxStore()
        val bus =
            MessageBus(
                busLocator,
                rootScope = backgroundScope,
                outbox = OutboxConfig(store = outboxStore),
                contexts =
                    mapOf(
                        BoundedContextId("healthy") to healthyLocator as HandlerLocator,
                        BoundedContextId("failing") to failingLocator,
                    ),
                inbox =
                    InboxConfig(
                        ackPolicy = InboxAckPolicy.HonourEventStrategy,
                        stores =
                            mapOf(
                                BoundedContextId("healthy") to healthyStore,
                                BoundedContextId("failing") to failingStore,
                            ),
                        pollInterval = 100.milliseconds,
                    ),
            )
        bus.start()

        bus.execute(PublishInboxAlphaCommand("event"))
        realDelay(400)

        assertTrue(healthyStore.fetchPending(10).isEmpty())
        assertEquals(1, healthyStore.markedConsumed.size)
        assertEquals(1, failingStore.fetchPending(10).size)
        assertTrue(failingStore.markedConsumed.isEmpty())
    }

    @Test
    fun aRedeliveredEnvelopeIsDedupedOnItsId_soHandlersRunOnce() = runTest {
        val outboxStore = NonAckingOutboxStore()
        val stores = HandlerFactoryStoreCollection()
        val busLocator = PersistingHandlerLocator(stores)
        registerPublishingCommand(stores)

        val received = mutableListOf<String>()
        val healthyLocator = PersistingHandlerLocator(stores)
        registerAlphaHandlerIn(stores, healthyLocator, received, "healthy")

        val bus =
            MessageBus(
                busLocator,
                rootScope = backgroundScope,
                outbox =
                    OutboxConfig(
                        store = outboxStore,
                        pollInterval = 50.milliseconds,
                        opportunisticDrain = false,
                    ),
                contexts = mapOf(BoundedContextId("healthy") to healthyLocator as HandlerLocator),
                inbox =
                    InboxConfig(
                        ackPolicy = InboxAckPolicy.HonourEventStrategy,
                        stores = mapOf(BoundedContextId("healthy") to RecordingInboxStore()),
                    ),
            )
        bus.start()

        bus.execute(PublishInboxAlphaCommand("event"))
        realDelay(400)

        assertTrue(
            outboxStore.fetchCount.size >= 3,
            "expected multiple route attempts: ${outboxStore.fetchCount}",
        )
        assertEquals(listOf("healthy:event"), received)
    }

    @Test
    fun aContextWithoutAnInboxStore_keepsSynchronousDispatch() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val busLocator = PersistingHandlerLocator(stores)
        registerPublishingCommand(stores)

        val plainReceived = mutableListOf<String>()
        val inboxedReceived = mutableListOf<String>()
        val plainLocator = PersistingHandlerLocator(stores)
        val inboxedLocator = PersistingHandlerLocator(stores)
        registerAlphaHandlerIn(stores, plainLocator, plainReceived, "plain")
        registerSecondAlphaHandlerIn(stores, inboxedLocator, inboxedReceived, "inboxed")

        val inboxedStore = RecordingInboxStore()
        val bus =
            MessageBus(
                busLocator,
                rootScope = backgroundScope,
                contexts =
                    mapOf(
                        BoundedContextId("plain") to plainLocator as HandlerLocator,
                        BoundedContextId("inboxed") to inboxedLocator,
                    ),
                inbox =
                    InboxConfig(
                        ackPolicy = InboxAckPolicy.HonourEventStrategy,
                        stores = mapOf(BoundedContextId("inboxed") to inboxedStore),
                        opportunisticDispatch = false,
                    ),
            )
        bus.start()

        bus.execute(PublishInboxAlphaCommand("event"))
        advanceUntilIdle()

        assertEquals(listOf("plain:event"), plainReceived)
        assertEquals(1, inboxedStore.saved.size)
        assertTrue(inboxedReceived.isEmpty(), "the inboxed context has not dispatched yet")
    }

    @Test
    fun aBusWithAnInboxButNoOutbox_mustBeStarted() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val busLocator = PersistingHandlerLocator(stores)
        registerPublishingCommand(stores)
        val healthyLocator = PersistingHandlerLocator(stores)
        registerAlphaHandlerIn(stores, healthyLocator, mutableListOf(), "healthy")

        val bus =
            MessageBus(
                busLocator,
                rootScope = backgroundScope,
                contexts = mapOf(BoundedContextId("healthy") to healthyLocator as HandlerLocator),
                inbox =
                    InboxConfig(
                        ackPolicy = InboxAckPolicy.HonourEventStrategy,
                        stores = mapOf(BoundedContextId("healthy") to RecordingInboxStore()),
                    ),
            )

        assertFailsWith<IllegalStateException> { bus.execute(PublishInboxAlphaCommand("event")) }
    }

    @Test
    fun nothingPumpsBeforeStart() = runTest {
        val store = RecordingInboxStore()
        val stores = HandlerFactoryStoreCollection()
        val busLocator = PersistingHandlerLocator(stores)
        registerPublishingCommand(stores)
        val healthyLocator = PersistingHandlerLocator(stores)
        registerAlphaHandlerIn(stores, healthyLocator, mutableListOf(), "healthy")

        MessageBus(
            busLocator,
            rootScope = backgroundScope,
            contexts = mapOf(BoundedContextId("healthy") to healthyLocator as HandlerLocator),
            inbox =
                InboxConfig(
                    ackPolicy = InboxAckPolicy.HonourEventStrategy,
                    stores = mapOf(BoundedContextId("healthy") to store),
                    pollInterval = 10.milliseconds,
                ),
        )
        realDelay(50)

        assertTrue(store.fetchLimits.isEmpty())
    }

    @Test
    fun stop_haltsThePumps() = runTest {
        val store = RecordingInboxStore()
        val stores = HandlerFactoryStoreCollection()
        val busLocator = PersistingHandlerLocator(stores)
        registerPublishingCommand(stores)
        val healthyLocator = PersistingHandlerLocator(stores)
        registerAlphaHandlerIn(stores, healthyLocator, mutableListOf(), "healthy")

        val bus =
            MessageBus(
                busLocator,
                rootScope = backgroundScope,
                contexts = mapOf(BoundedContextId("healthy") to healthyLocator as HandlerLocator),
                inbox =
                    InboxConfig(
                        ackPolicy = InboxAckPolicy.HonourEventStrategy,
                        stores = mapOf(BoundedContextId("healthy") to store),
                        pollInterval = 20.milliseconds,
                    ),
            )

        bus.start()
        realDelay(50)
        val pumpsBeforeStop = store.fetchLimits.size
        assertTrue(pumpsBeforeStop > 0)

        bus.stop()
        realDelay(100)

        assertEquals(pumpsBeforeStop, store.fetchLimits.size)
    }

    @Test
    fun aFailingIntegrationHandlerNeverFailsTheCommand() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val busLocator = PersistingHandlerLocator(stores)
        registerPublishingCommand(stores)
        val failingLocator = PersistingHandlerLocator(stores)
        registerThrowingHandlerIn(stores, failingLocator, mutableListOf())

        val bus =
            MessageBus(
                busLocator,
                rootScope = backgroundScope,
                outbox = OutboxConfig(store = RecordingOutboxStore()),
                contexts = mapOf(BoundedContextId("failing") to failingLocator as HandlerLocator),
                inbox =
                    InboxConfig(
                        ackPolicy = InboxAckPolicy.HonourEventStrategy,
                        stores = mapOf(BoundedContextId("failing") to RecordingInboxStore()),
                    ),
            )
        bus.start()

        val result = bus.execute(PublishInboxAlphaCommand("event"))
        advanceUntilIdle()

        assertTrue(result.isSuccess)
    }

    @Test
    fun envelopesLeftPendingByAPreviousProcessAreDispatchedOnStart() = runTest {
        val store = RecordingInboxStore()
        store.save(listOf(EventEnvelope.of(InboxAlphaEvent("from-before-crash"))))
        val stores = HandlerFactoryStoreCollection()
        val busLocator = PersistingHandlerLocator(stores)
        val healthyReceived = mutableListOf<String>()
        val healthyLocator = PersistingHandlerLocator(stores)
        registerAlphaHandlerIn(stores, healthyLocator, healthyReceived, "healthy")

        val bus =
            MessageBus(
                busLocator,
                rootScope = backgroundScope,
                contexts = mapOf(BoundedContextId("healthy") to healthyLocator as HandlerLocator),
                inbox =
                    InboxConfig(
                        ackPolicy = InboxAckPolicy.HonourEventStrategy,
                        stores = mapOf(BoundedContextId("healthy") to store),
                        pollInterval = 10.milliseconds,
                    ),
            )
        bus.start()
        realDelay(50)

        assertEquals(listOf("healthy:from-before-crash"), healthyReceived)
    }

    /** The default FireAndForget strategy is not acked until its handler completes. */
    @Test
    fun aDefaultSettingsEventIsNotAckedUntilItsHandlerCompletes() = runTest {
        val outboxStore = RecordingOutboxStore()
        val stores = HandlerFactoryStoreCollection()
        val busLocator = PersistingHandlerLocator(stores)
        stores.commandStore.registerHandlers(
            PublishGatedInboxCommand::class,
            listOf(
                CommandHandlerFactory(PublishGatedInboxCommandHandler::class) {
                    PublishGatedInboxCommandHandler()
                }
            ),
        )

        val received = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val locator = PersistingHandlerLocator(stores)
        stores.eventStore.registerHandlers(
            GatedInboxEvent::class,
            listOf(
                EventHandlerFactory(GatedInboxHandler::class) { GatedInboxHandler(received, gate) }
            ),
        )
        locator.integrationEventMapper.addEventHandlers(
            GatedInboxEvent::class,
            listOf(GatedInboxHandler::class),
        )

        val inboxStore = RecordingInboxStore()
        val bus =
            MessageBus(
                busLocator,
                rootScope = backgroundScope,
                outbox = OutboxConfig(store = outboxStore, pollInterval = 10.seconds),
                contexts = mapOf(BoundedContextId("healthy") to locator as HandlerLocator),
                inbox =
                    InboxConfig(
                        ackPolicy = InboxAckPolicy.HonourEventStrategy,
                        stores = mapOf(BoundedContextId("healthy") to inboxStore),
                    ),
            )
        bus.start()

        bus.execute(PublishGatedInboxCommand("event"))
        realDelay(100)

        assertTrue(received.isEmpty(), "the handler is still suspended on the gate")
        assertTrue(
            inboxStore.markedConsumed.isEmpty(),
            "must not ack while the handler is still running",
        )

        gate.complete(Unit)
        realDelay(150)

        assertEquals(listOf("event"), received)
        assertEquals(1, inboxStore.markedConsumed.size)
    }

    @Test
    fun requireHandlerSuccess_leavesAFailedDefaultSettingsEventPending_andRetriesOnTheNextTick() =
        runTest {
            val stores = HandlerFactoryStoreCollection()
            val busLocator = PersistingHandlerLocator(stores)
            stores.commandStore.registerHandlers(
                PublishGatedInboxCommand::class,
                listOf(
                    CommandHandlerFactory(PublishGatedInboxCommandHandler::class) {
                        PublishGatedInboxCommandHandler()
                    }
                ),
            )

            val attempts = mutableListOf<String>()
            val gate = CompletableDeferred<Unit>()
            val locator = PersistingHandlerLocator(stores)
            stores.eventStore.registerHandlers(
                GatedInboxEvent::class,
                listOf(
                    EventHandlerFactory(ThrowingGatedInboxHandler::class) {
                        ThrowingGatedInboxHandler(attempts, gate)
                    }
                ),
            )
            locator.integrationEventMapper.addEventHandlers(
                GatedInboxEvent::class,
                listOf(ThrowingGatedInboxHandler::class),
            )

            val inboxStore = RecordingInboxStore()
            val bus =
                MessageBus(
                    busLocator,
                    rootScope = backgroundScope,
                    contexts = mapOf(BoundedContextId("healthy") to locator as HandlerLocator),
                    inbox =
                        InboxConfig(
                            stores = mapOf(BoundedContextId("healthy") to inboxStore),
                            pollInterval = 100.milliseconds,
                            ackPolicy = InboxAckPolicy.RequireHandlerSuccess,
                        ),
                )
            bus.start()

            bus.execute(PublishGatedInboxCommand("event"))
            gate.complete(Unit)
            realDelay(350)

            assertTrue(
                inboxStore.markedConsumed.isEmpty(),
                "a failed handler must leave the envelope pending, not acked",
            )
            assertTrue(
                attempts.size >= 2,
                "expected the pump to retry the still-pending envelope: $attempts",
            )
        }
}
