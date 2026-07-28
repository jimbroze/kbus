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
import com.jimbroze.kbus.core.module.BoundedContextId
import com.jimbroze.kbus.core.registry.HandlerLocator
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.testdoubles.advanceVirtualTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

/** [ErrorStrategy.FailFast] so a throwing handler surfaces as a failed delivery to the router. */
private class AlphaEvent(val name: String) : IntegrationEvent() {
    override val errorStrategy = ErrorStrategy.FailFast
}

private class BetaEvent(val name: String) : IntegrationEvent()

private class PublishAlphaCommand(val name: String) : Command<BusResult<Unit, MessageFailure>>()

private class PublishAlphaCommandHandler :
    CommandHandler<PublishAlphaCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: PublishAlphaCommand): BusResult<Unit, MessageFailure> {
        publish(AlphaEvent(message.name))
        return BusResult.success(Unit)
    }
}

private class RecordingAlphaHandler(
    private val received: MutableList<String>,
    private val label: String,
) : IntegrationEventHandler<AlphaEvent> {
    override suspend fun handle(message: AlphaEvent) {
        received.add("$label:${message.name}")
    }
}

private class ThrowingAlphaHandler(private val attempts: MutableList<String>) :
    IntegrationEventHandler<AlphaEvent> {
    override suspend fun handle(message: AlphaEvent) {
        attempts.add(message.name)
        error("context handler failed")
    }
}

private class RecordingBetaHandler(private val received: MutableList<String>) :
    IntegrationEventHandler<BetaEvent> {
    override suspend fun handle(message: BetaEvent) {
        received.add(message.name)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MessageBusMultiContextTest {

    private fun registerPublishingCommand(stores: HandlerFactoryStoreCollection) {
        stores.commandStore.registerHandlers(
            PublishAlphaCommand::class,
            listOf(
                CommandHandlerFactory(PublishAlphaCommandHandler::class) {
                    PublishAlphaCommandHandler()
                }
            ),
        )
    }

    /** Registers an alpha handler factory in the shared stores and maps it on [locator] only. */
    private fun registerAlphaHandlerIn(
        stores: HandlerFactoryStoreCollection,
        locator: PersistingHandlerLocator,
        received: MutableList<String>,
        label: String,
    ) {
        stores.eventStore.registerHandlers(
            AlphaEvent::class,
            listOf(
                EventHandlerFactory(RecordingAlphaHandler::class) {
                    RecordingAlphaHandler(received, label)
                }
            ),
        )
        locator.integrationEventMapper.addEventHandlers(
            AlphaEvent::class,
            listOf(RecordingAlphaHandler::class),
        )
    }

    private fun registerBetaHandlerIn(
        stores: HandlerFactoryStoreCollection,
        locator: PersistingHandlerLocator,
        received: MutableList<String>,
    ) {
        stores.eventStore.registerHandlers(
            BetaEvent::class,
            listOf(
                EventHandlerFactory(RecordingBetaHandler::class) { RecordingBetaHandler(received) }
            ),
        )
        locator.integrationEventMapper.addEventHandlers(
            BetaEvent::class,
            listOf(RecordingBetaHandler::class),
        )
    }

    @Test
    fun withNoContextsConfigured_theDefaultContextReceivesEveryEvent() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        val received = mutableListOf<String>()
        registerPublishingCommand(stores)
        registerAlphaHandlerIn(stores, locator, received, "default")

        val bus = MessageBus(locator, appScope = backgroundScope)

        bus.execute(PublishAlphaCommand("event"))
        advanceUntilIdle()
        advanceVirtualTime(100)

        assertEquals(listOf("default:event"), received)
    }

    @Test
    fun aHandlerInOneContextDoesNotFireForAnotherContextsEvent() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val busLocator = PersistingHandlerLocator(stores)
        registerPublishingCommand(stores)

        val alphaReceived = mutableListOf<String>()
        val betaReceived = mutableListOf<String>()
        val alphaLocator = PersistingHandlerLocator(stores)
        val betaLocator = PersistingHandlerLocator(stores)
        registerAlphaHandlerIn(stores, alphaLocator, alphaReceived, "alpha")
        registerBetaHandlerIn(stores, betaLocator, betaReceived)

        val bus =
            MessageBus(
                busLocator,
                appScope = backgroundScope,
                contexts =
                    mapOf(
                        BoundedContextId("alpha") to alphaLocator as HandlerLocator,
                        BoundedContextId("beta") to betaLocator,
                    ),
            )

        bus.execute(PublishAlphaCommand("event"))
        advanceUntilIdle()
        advanceVirtualTime(100)

        assertEquals(listOf("alpha:event"), alphaReceived)
        assertTrue(betaReceived.isEmpty())
    }

    @Test
    fun twoContextsWithAHandlerForTheSameEvent_eachFiresExactlyOnce() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val busLocator = PersistingHandlerLocator(stores)
        registerPublishingCommand(stores)

        val received = mutableListOf<String>()
        val alphaLocator = PersistingHandlerLocator(stores)
        val betaLocator = PersistingHandlerLocator(stores)
        registerAlphaHandlerIn(stores, alphaLocator, received, "alpha")
        betaLocator.integrationEventMapper.addEventHandlers(
            AlphaEvent::class,
            listOf(RecordingAlphaHandler::class),
        )

        val bus =
            MessageBus(
                busLocator,
                appScope = backgroundScope,
                contexts =
                    mapOf(
                        BoundedContextId("alpha") to alphaLocator as HandlerLocator,
                        BoundedContextId("beta") to betaLocator,
                    ),
            )

        bus.execute(PublishAlphaCommand("event"))
        advanceUntilIdle()
        advanceVirtualTime(100)

        assertEquals(2, received.size)
    }

    @Test
    fun anEventNoContextSubscribesTo_isStillObserved() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val busLocator = PersistingHandlerLocator(stores)
        registerPublishingCommand(stores)
        val betaLocator = PersistingHandlerLocator(stores)
        registerBetaHandlerIn(stores, betaLocator, mutableListOf())

        val bus =
            MessageBus(
                busLocator,
                appScope = backgroundScope,
                contexts = mapOf(BoundedContextId("beta") to betaLocator as HandlerLocator),
            )

        val observed = mutableListOf<AlphaEvent>()
        val job = launch { bus.observe<AlphaEvent>().take(1).toList(observed) }
        yield()

        bus.execute(PublishAlphaCommand("unsubscribed"))
        advanceUntilIdle()
        job.join()

        assertEquals(listOf("unsubscribed"), observed.map { it.name })
    }

    @Test
    fun anEventNoContextSubscribesTo_isAckedByTheOutbox_notRetriedForever() = runTest {
        val store = MarkRecordingOutboxStore()
        val stores = HandlerFactoryStoreCollection()
        val busLocator = PersistingHandlerLocator(stores)
        registerPublishingCommand(stores)
        val betaLocator = PersistingHandlerLocator(stores)
        registerBetaHandlerIn(stores, betaLocator, mutableListOf())

        val bus =
            MessageBus(
                busLocator,
                appScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
                contexts = mapOf(BoundedContextId("beta") to betaLocator as HandlerLocator),
            )
        bus.start()
        advanceVirtualTime(50)

        bus.execute(PublishAlphaCommand("unsubscribed"))
        advanceVirtualTime(150)

        assertEquals(1, store.markedPublished.size)
        assertTrue(store.fetchUnpublished(10).isEmpty())
    }

    @Test
    fun aFailingContextLeavesTheEntryUnpublished_andHealthyContextsReDispatchOnRetry() = runTest {
        val store = MarkRecordingOutboxStore()
        val stores = HandlerFactoryStoreCollection()
        val busLocator = PersistingHandlerLocator(stores)
        registerPublishingCommand(stores)

        val healthyReceived = mutableListOf<String>()
        val failedAttempts = mutableListOf<String>()
        val healthyLocator = PersistingHandlerLocator(stores)
        val failingLocator = PersistingHandlerLocator(stores)
        registerAlphaHandlerIn(stores, healthyLocator, healthyReceived, "healthy")
        stores.eventStore.registerHandlers(
            AlphaEvent::class,
            listOf(
                EventHandlerFactory(ThrowingAlphaHandler::class) {
                    ThrowingAlphaHandler(failedAttempts)
                }
            ),
        )
        failingLocator.integrationEventMapper.addEventHandlers(
            AlphaEvent::class,
            listOf(ThrowingAlphaHandler::class),
        )

        val bus =
            MessageBus(
                busLocator,
                appScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 100.milliseconds),
                contexts =
                    mapOf(
                        BoundedContextId("healthy") to healthyLocator as HandlerLocator,
                        BoundedContextId("failing") to failingLocator,
                    ),
            )
        bus.start()

        bus.execute(PublishAlphaCommand("event"))
        advanceVirtualTime(400)

        // The whole entry stays unpublished, so the poller re-routes it to *every* context and the
        // healthy one re-dispatches each cycle. This bus configures no inbox — see
        // MessageBusInboxTest.aFailingContextIsRetriedAlone_theOutboxEntryIsAckedAndHealthyContextsDispatchOnce
        // for what an inbox buys.
        assertTrue(failedAttempts.size >= 2, "expected the poller to retry: $failedAttempts")
        assertTrue(
            healthyReceived.size >= 2,
            "expected re-delivery amplification: $healthyReceived",
        )
        assertTrue(store.markedPublished.isEmpty())
    }
}

private class MarkRecordingOutboxStore : OutboxStore {
    private val entries = mutableListOf<EventEnvelope>()
    val markedPublished = mutableListOf<String>()

    override suspend fun save(entries: List<EventEnvelope>) {
        this.entries.addAll(entries)
    }

    override suspend fun fetchUnpublished(limit: Int): List<EventEnvelope> = entries.take(limit)

    // Idempotent, like a real store: re-marking an already-published id is a no-op, not a second
    // entry — see RecordingOutboxStore.
    override suspend fun markPublished(ids: List<String>) {
        ids.forEach { if (it !in markedPublished) markedPublished.add(it) }
        entries.removeAll { it.id in ids }
    }
}
