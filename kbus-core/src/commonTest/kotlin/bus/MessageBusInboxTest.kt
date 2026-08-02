package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.ErrorStrategy
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.contracts.outbox.OutboxStore
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.core.fixtures.RecordingInboxStore
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.module.BoundedContext
import com.jimbroze.kbus.core.module.BoundedContextId
import com.jimbroze.kbus.core.module.inbox.ContextInbox
import com.jimbroze.kbus.core.module.inbox.InboxAckPolicy
import com.jimbroze.kbus.core.module.inbox.InboxConfig
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.testdoubles.advanceVirtualTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** [ErrorStrategy.FailFast] so a throwing handler surfaces synchronously. */
private class InboxAlphaEvent(val name: String) : IntegrationEvent() {
    override val errorStrategy = ErrorStrategy.FailFast
}

private class PublishInboxAlphaCommand(val name: String) :
    Command<BusResult<Unit, MessageFailure>>()

private class PublishInboxAlphaCommandHandler(
    private val integrationEventPublisher: IntegrationEventPublisher
) : CommandHandler<PublishInboxAlphaCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(
        message: PublishInboxAlphaCommand
    ): BusResult<Unit, MessageFailure> {
        integrationEventPublisher.publish(listOf(InboxAlphaEvent(message.name)))
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
    val fetchLimits = mutableListOf<Int>()

    override suspend fun save(entries: List<EventEnvelope>) {
        this.entries.addAll(entries)
    }

    override suspend fun fetchUnpublished(limit: Int): List<EventEnvelope> {
        fetchLimits.add(limit)
        return entries.take(limit)
    }

    override suspend fun markPublished(ids: List<String>) {
        // Never acks: simulates a producer that can't mark entries published, so every poll
        // re-routes the same envelope.
    }
}

/** Default settings: concurrent dispatch, fire and forget. */
private class GatedInboxEvent(val name: String) : IntegrationEvent()

private class PublishGatedInboxCommand(val name: String) :
    Command<BusResult<Unit, MessageFailure>>()

private class PublishGatedInboxCommandHandler(
    private val integrationEventPublisher: IntegrationEventPublisher
) : CommandHandler<PublishGatedInboxCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(
        message: PublishGatedInboxCommand
    ): BusResult<Unit, MessageFailure> {
        integrationEventPublisher.publish(listOf(GatedInboxEvent(message.name)))
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

    private fun registerPublishingCommand(stores: HandlerFactoryStoreCollection) {
        stores.commandStore.registerHandlers(
            PublishInboxAlphaCommand::class,
            listOf(
                CommandHandlerFactory(PublishInboxAlphaCommandHandler::class) {
                    PublishInboxAlphaCommandHandler(it.integrationEventPublisher)
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
            val busStores = HandlerFactoryStoreCollection()
            val busLocator = PersistingHandlerLocator(busStores)
            registerPublishingCommand(busStores)

            val healthyReceived = mutableListOf<String>()
            val failedAttempts = mutableListOf<String>()
            val healthyStores = HandlerFactoryStoreCollection()
            val failingStores = HandlerFactoryStoreCollection()
            val healthyLocator = PersistingHandlerLocator(healthyStores)
            val failingLocator = PersistingHandlerLocator(failingStores)
            registerAlphaHandlerIn(healthyStores, healthyLocator, healthyReceived, "healthy")
            registerThrowingHandlerIn(failingStores, failingLocator, failedAttempts)

            val bus =
                MessageBus(
                    busLocator,
                    appScope = backgroundScope,
                    outbox = OutboxConfig(store = outboxStore),
                    contexts =
                        listOf(
                            BoundedContext(BoundedContextId("publisher"), busLocator),
                            BoundedContext(
                                BoundedContextId("healthy"),
                                healthyLocator,
                                inbox =
                                    ContextInbox(
                                        RecordingInboxStore(),
                                        InboxAckPolicy.HonourEventStrategy,
                                    ),
                            ),
                            BoundedContext(
                                BoundedContextId("failing"),
                                failingLocator,
                                inbox =
                                    ContextInbox(
                                        RecordingInboxStore(),
                                        InboxAckPolicy.HonourEventStrategy,
                                    ),
                            ),
                        ),
                    inbox = InboxConfig(pollInterval = 100.milliseconds),
                )
            bus.start()

            bus.execute(PublishInboxAlphaCommand("event"))
            advanceVirtualTime(600)

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
        val busStores = HandlerFactoryStoreCollection()
        val busLocator = PersistingHandlerLocator(busStores)
        registerPublishingCommand(busStores)

        val healthyStores = HandlerFactoryStoreCollection()
        val failingStores = HandlerFactoryStoreCollection()
        val healthyLocator = PersistingHandlerLocator(healthyStores)
        val failingLocator = PersistingHandlerLocator(failingStores)
        registerAlphaHandlerIn(healthyStores, healthyLocator, mutableListOf(), "healthy")
        registerThrowingHandlerIn(failingStores, failingLocator, mutableListOf())

        val healthyStore = RecordingInboxStore()
        val failingStore = RecordingInboxStore()
        val bus =
            MessageBus(
                busLocator,
                appScope = backgroundScope,
                outbox = OutboxConfig(store = outboxStore),
                contexts =
                    listOf(
                        BoundedContext(BoundedContextId("publisher"), busLocator),
                        BoundedContext(
                            BoundedContextId("healthy"),
                            healthyLocator,
                            inbox = ContextInbox(healthyStore, InboxAckPolicy.HonourEventStrategy),
                        ),
                        BoundedContext(
                            BoundedContextId("failing"),
                            failingLocator,
                            inbox = ContextInbox(failingStore, InboxAckPolicy.HonourEventStrategy),
                        ),
                    ),
                inbox = InboxConfig(pollInterval = 100.milliseconds),
            )
        bus.start()

        bus.execute(PublishInboxAlphaCommand("event"))
        advanceVirtualTime(400)

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
                appScope = backgroundScope,
                outbox =
                    OutboxConfig(
                        store = outboxStore,
                        pollInterval = 50.milliseconds,
                        opportunisticDrain = false,
                    ),
                contexts =
                    listOf(
                        BoundedContext(
                            BoundedContextId("healthy"),
                            healthyLocator,
                            inbox =
                                ContextInbox(
                                    RecordingInboxStore(),
                                    InboxAckPolicy.HonourEventStrategy,
                                ),
                        )
                    ),
            )
        bus.start()

        bus.execute(PublishInboxAlphaCommand("event"))
        advanceVirtualTime(400)

        assertTrue(
            outboxStore.fetchLimits.size >= 3,
            "expected multiple route attempts: ${outboxStore.fetchLimits}",
        )
        assertEquals(listOf("healthy:event"), received)
    }

    @Test
    fun aContextWithoutAnInboxStore_keepsSynchronousDispatch() = runTest {
        val busStores = HandlerFactoryStoreCollection()
        val busLocator = PersistingHandlerLocator(busStores)
        registerPublishingCommand(busStores)

        val plainReceived = mutableListOf<String>()
        val inboxedReceived = mutableListOf<String>()
        val plainStores = HandlerFactoryStoreCollection()
        val inboxedStores = HandlerFactoryStoreCollection()
        val plainLocator = PersistingHandlerLocator(plainStores)
        val inboxedLocator = PersistingHandlerLocator(inboxedStores)
        registerAlphaHandlerIn(plainStores, plainLocator, plainReceived, "plain")
        registerSecondAlphaHandlerIn(inboxedStores, inboxedLocator, inboxedReceived, "inboxed")

        val inboxedStore = RecordingInboxStore()
        val bus =
            MessageBus(
                busLocator,
                appScope = backgroundScope,
                contexts =
                    listOf(
                        BoundedContext(BoundedContextId("publisher"), busLocator),
                        BoundedContext(BoundedContextId("plain"), plainLocator),
                        BoundedContext(
                            BoundedContextId("inboxed"),
                            inboxedLocator,
                            inbox = ContextInbox(inboxedStore, InboxAckPolicy.HonourEventStrategy),
                        ),
                    ),
                inbox = InboxConfig(opportunisticDispatch = false),
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
                appScope = backgroundScope,
                contexts =
                    listOf(
                        BoundedContext(
                            BoundedContextId("healthy"),
                            healthyLocator,
                            inbox =
                                ContextInbox(
                                    RecordingInboxStore(),
                                    InboxAckPolicy.HonourEventStrategy,
                                ),
                        )
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
            appScope = backgroundScope,
            contexts =
                listOf(
                    BoundedContext(
                        BoundedContextId("healthy"),
                        healthyLocator,
                        inbox = ContextInbox(store, InboxAckPolicy.HonourEventStrategy),
                    )
                ),
            inbox = InboxConfig(pollInterval = 10.milliseconds),
        )
        advanceVirtualTime(50)

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
                appScope = backgroundScope,
                contexts =
                    listOf(
                        BoundedContext(
                            BoundedContextId("healthy"),
                            healthyLocator,
                            inbox = ContextInbox(store, InboxAckPolicy.HonourEventStrategy),
                        )
                    ),
                inbox = InboxConfig(pollInterval = 20.milliseconds),
            )

        bus.start()
        advanceVirtualTime(50)
        val pumpsBeforeStop = store.fetchLimits.size
        assertTrue(pumpsBeforeStop > 0)

        bus.stop()
        advanceVirtualTime(100)

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
                appScope = backgroundScope,
                outbox = OutboxConfig(store = RecordingOutboxStore()),
                contexts =
                    listOf(
                        BoundedContext(
                            BoundedContextId("failing"),
                            failingLocator,
                            inbox =
                                ContextInbox(
                                    RecordingInboxStore(),
                                    InboxAckPolicy.HonourEventStrategy,
                                ),
                        )
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
                appScope = backgroundScope,
                contexts =
                    listOf(
                        BoundedContext(
                            BoundedContextId("healthy"),
                            healthyLocator,
                            inbox = ContextInbox(store, InboxAckPolicy.HonourEventStrategy),
                        )
                    ),
                inbox = InboxConfig(pollInterval = 10.milliseconds),
            )
        bus.start()
        advanceVirtualTime(50)

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
                    PublishGatedInboxCommandHandler(it.integrationEventPublisher)
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
                appScope = backgroundScope,
                outbox = OutboxConfig(store = outboxStore, pollInterval = 10.seconds),
                contexts =
                    listOf(
                        BoundedContext(
                            BoundedContextId("healthy"),
                            locator,
                            inbox = ContextInbox(inboxStore, InboxAckPolicy.HonourEventStrategy),
                        )
                    ),
            )
        bus.start()

        bus.execute(PublishGatedInboxCommand("event"))
        advanceVirtualTime(100)

        assertTrue(received.isEmpty(), "the handler is still suspended on the gate")
        assertTrue(
            inboxStore.markedConsumed.isEmpty(),
            "must not ack while the handler is still running",
        )

        gate.complete(Unit)
        advanceVirtualTime(150)

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
                        PublishGatedInboxCommandHandler(it.integrationEventPublisher)
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
                    appScope = backgroundScope,
                    contexts =
                        listOf(
                            BoundedContext(
                                BoundedContextId("healthy"),
                                locator,
                                inbox =
                                    ContextInbox(inboxStore, InboxAckPolicy.RequireHandlerSuccess),
                            )
                        ),
                    inbox = InboxConfig(pollInterval = 100.milliseconds),
                )
            bus.start()

            bus.execute(PublishGatedInboxCommand("event"))
            gate.complete(Unit)
            advanceVirtualTime(350)

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
