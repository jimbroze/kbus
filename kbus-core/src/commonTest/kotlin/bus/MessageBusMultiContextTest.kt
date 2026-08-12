package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.contracts.common.AmbiguousHandlerException
import com.jimbroze.kbus.contracts.common.MissingHandlerException
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.ErrorStrategy
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.messages.query.QueryHandler
import com.jimbroze.kbus.contracts.outbox.OutboxStore
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.core.boundedcontext.BoundedContext
import com.jimbroze.kbus.core.boundedcontext.BoundedContextId
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.registry.persisting.store.QueryHandlerFactory
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import com.jimbroze.kbus.domain.event.DomainEventPublisher
import com.jimbroze.kbus.testdoubles.advanceVirtualTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

private class PublishAlphaCommandHandler(
    private val integrationEventPublisher: IntegrationEventPublisher
) : CommandHandler<PublishAlphaCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: PublishAlphaCommand): BusResult<Unit, MessageFailure> {
        integrationEventPublisher.publish(listOf(AlphaEvent(message.name)))
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

private class DeltaDomainEvent(val name: String) : DomainEvent()

private class PublishDeltaCommand(val name: String) : Command<BusResult<Unit, MessageFailure>>()

private class PublishDeltaCommandHandler(private val domainEventPublisher: DomainEventPublisher) :
    CommandHandler<PublishDeltaCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: PublishDeltaCommand): BusResult<Unit, MessageFailure> {
        domainEventPublisher.publish(DeltaDomainEvent(message.name))
        return BusResult.success(Unit)
    }
}

/**
 * A second, distinct command class — a command is single-owner, so isolation tests that need two
 * contexts each publishing [DeltaDomainEvent] need two commands, one per owning context.
 */
private class PublishSecondDeltaCommand(val name: String) :
    Command<BusResult<Unit, MessageFailure>>()

private class PublishSecondDeltaCommandHandler(
    private val domainEventPublisher: DomainEventPublisher
) : CommandHandler<PublishSecondDeltaCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(
        message: PublishSecondDeltaCommand
    ): BusResult<Unit, MessageFailure> {
        domainEventPublisher.publish(DeltaDomainEvent(message.name))
        return BusResult.success(Unit)
    }
}

private class RecordingDeltaHandler(
    private val received: MutableList<String>,
    private val label: String,
) : DomainEventHandler<DeltaDomainEvent>() {
    override suspend fun handle(message: DeltaDomainEvent) {
        received.add("$label:${message.name}")
    }
}

private class AlphaQuery : Query<BusResult<String, MessageFailure>>()

private class AlphaQueryHandler : QueryHandler<AlphaQuery, BusResult<String, MessageFailure>>() {
    override suspend fun handle(message: AlphaQuery): BusResult<String, MessageFailure> =
        BusResult.success("alpha")
}

@OptIn(ExperimentalCoroutinesApi::class)
class MessageBusMultiContextTest {

    private fun registerPublishingCommand(stores: HandlerFactoryStoreCollection) {
        stores.commandStore.registerHandlers(
            PublishAlphaCommand::class,
            listOf(
                CommandHandlerFactory(PublishAlphaCommandHandler::class) {
                    PublishAlphaCommandHandler(it.integrationEventPublisher)
                }
            ),
        )
    }

    /** Registers an alpha handler factory in [stores] and maps it on [locator] only. */
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

    private fun registerPublishingDeltaCommand(stores: HandlerFactoryStoreCollection) {
        stores.commandStore.registerHandlers(
            PublishDeltaCommand::class,
            listOf(
                CommandHandlerFactory(PublishDeltaCommandHandler::class) { deps ->
                    PublishDeltaCommandHandler(deps.domainEventPublisher)
                }
            ),
        )
    }

    private fun registerPublishingSecondDeltaCommand(stores: HandlerFactoryStoreCollection) {
        stores.commandStore.registerHandlers(
            PublishSecondDeltaCommand::class,
            listOf(
                CommandHandlerFactory(PublishSecondDeltaCommandHandler::class) { deps ->
                    PublishSecondDeltaCommandHandler(deps.domainEventPublisher)
                }
            ),
        )
    }

    /** Registers a domain handler factory in [stores] and maps it on [locator] only. */
    private fun registerDeltaHandlerIn(
        stores: HandlerFactoryStoreCollection,
        locator: PersistingHandlerLocator,
        received: MutableList<String>,
        label: String,
    ) {
        stores.eventStore.registerHandlers(
            DeltaDomainEvent::class,
            listOf(
                EventHandlerFactory(RecordingDeltaHandler::class) {
                    RecordingDeltaHandler(received, label)
                }
            ),
        )
        locator.domainEventMapper.addDomainHandlers(
            DeltaDomainEvent::class,
            listOf(RecordingDeltaHandler::class),
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

    /**
     * The command lives in its own store, wrapped as its own context — a command is single-owner,
     * so it cannot share a store (and therefore ownership) with any of the event-handling contexts
     * under test.
     */
    private fun publisherContext(): BoundedContext {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        registerPublishingCommand(stores)
        return BoundedContext(BoundedContextId("publisher"), locator)
    }

    @Test
    fun `delivers every event to the implicit default context when none is configured`() = runTest {
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
    fun `refuses an empty list of contexts`() = runTest {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                MessageBus(contexts = emptyList(), appScope = backgroundScope)
            }

        assertTrue(exception.message!!.contains("at least one bounded context"))
    }

    @Test
    fun `refuses two contexts sharing a bounded context id`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val firstLocator = PersistingHandlerLocator(stores)
        val secondLocator = PersistingHandlerLocator(stores)

        val exception =
            assertFailsWith<IllegalArgumentException> {
                MessageBus(
                    appScope = backgroundScope,
                    contexts =
                        listOf(
                            BoundedContext(BoundedContextId("alpha"), firstLocator),
                            BoundedContext(BoundedContextId("alpha"), secondLocator),
                        ),
                )
            }
        assertTrue(exception.message!!.contains("alpha"))
    }

    @Test
    fun `executes a command exactly one context owns`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        registerPublishingCommand(stores)

        val bus =
            MessageBus(
                appScope = backgroundScope,
                contexts = listOf(BoundedContext(BoundedContextId("alpha"), locator)),
            )

        val result = bus.execute(PublishAlphaCommand("event"))

        assertTrue(result.isSuccess)
    }

    @Test
    fun `refuses at construction a command two contexts claim`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        registerPublishingCommand(stores)
        val firstLocator = PersistingHandlerLocator(stores)
        val secondLocator = PersistingHandlerLocator(stores)

        assertFailsWith<AmbiguousHandlerException> {
            MessageBus(
                appScope = backgroundScope,
                contexts =
                    listOf(
                        BoundedContext(BoundedContextId("alpha"), firstLocator),
                        BoundedContext(BoundedContextId("beta"), secondLocator),
                    ),
            )
        }
    }

    @Test
    fun `refuses a command no context owns`() = runTest {
        val bus =
            MessageBus(
                appScope = backgroundScope,
                contexts =
                    listOf(BoundedContext(BoundedContextId("alpha"), PersistingHandlerLocator())),
            )

        assertFailsWith<MissingHandlerException> { bus.execute(PublishAlphaCommand("event")) }
    }

    @Test
    fun `fetches a query exactly one context owns`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        stores.queryStore.registerHandlers(
            AlphaQuery::class,
            listOf(QueryHandlerFactory(AlphaQueryHandler::class) { AlphaQueryHandler() }),
        )

        val bus =
            MessageBus(
                appScope = backgroundScope,
                contexts = listOf(BoundedContext(BoundedContextId("alpha"), locator)),
            )

        val result = bus.fetch(AlphaQuery())

        assertEquals("alpha", result.getOrNull())
    }

    @Test
    fun `refuses at construction a query two contexts claim`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        stores.queryStore.registerHandlers(
            AlphaQuery::class,
            listOf(QueryHandlerFactory(AlphaQueryHandler::class) { AlphaQueryHandler() }),
        )
        val firstLocator = PersistingHandlerLocator(stores)
        val secondLocator = PersistingHandlerLocator(stores)

        assertFailsWith<AmbiguousHandlerException> {
            MessageBus(
                appScope = backgroundScope,
                contexts =
                    listOf(
                        BoundedContext(BoundedContextId("alpha"), firstLocator),
                        BoundedContext(BoundedContextId("beta"), secondLocator),
                    ),
            )
        }
    }

    @Test
    fun `refuses a query no context owns`() = runTest {
        val bus =
            MessageBus(
                appScope = backgroundScope,
                contexts =
                    listOf(BoundedContext(BoundedContextId("alpha"), PersistingHandlerLocator())),
            )

        assertFailsWith<MissingHandlerException> { bus.fetch(AlphaQuery()) }
    }

    @Test
    fun `keeps a context's domain handler out of another context's command`() = runTest {
        val ownerStores = HandlerFactoryStoreCollection()
        val ownerLocator = PersistingHandlerLocator(ownerStores)
        registerPublishingDeltaCommand(ownerStores)

        val otherStores = HandlerFactoryStoreCollection()
        val otherLocator = PersistingHandlerLocator(otherStores)
        val otherReceived = mutableListOf<String>()
        registerDeltaHandlerIn(otherStores, otherLocator, otherReceived, "beta")

        val bus =
            MessageBus(
                appScope = backgroundScope,
                contexts =
                    listOf(
                        BoundedContext(BoundedContextId("alpha"), ownerLocator),
                        BoundedContext(BoundedContextId("beta"), otherLocator),
                    ),
            )

        bus.execute(PublishDeltaCommand("event"))
        advanceUntilIdle()
        advanceVirtualTime(100)

        assertTrue(otherReceived.isEmpty())
    }

    @Test
    fun `runs each context's domain handler only for that context's own command`() = runTest {
        val alphaStores = HandlerFactoryStoreCollection()
        val alphaLocator = PersistingHandlerLocator(alphaStores)
        registerPublishingDeltaCommand(alphaStores)
        val alphaReceived = mutableListOf<String>()
        registerDeltaHandlerIn(alphaStores, alphaLocator, alphaReceived, "alpha")

        val betaStores = HandlerFactoryStoreCollection()
        val betaLocator = PersistingHandlerLocator(betaStores)
        registerPublishingSecondDeltaCommand(betaStores)
        val betaReceived = mutableListOf<String>()
        registerDeltaHandlerIn(betaStores, betaLocator, betaReceived, "beta")

        val bus =
            MessageBus(
                appScope = backgroundScope,
                contexts =
                    listOf(
                        BoundedContext(BoundedContextId("alpha"), alphaLocator),
                        BoundedContext(BoundedContextId("beta"), betaLocator),
                    ),
            )

        bus.execute(PublishDeltaCommand("from-alpha"))
        advanceUntilIdle()
        advanceVirtualTime(100)

        assertEquals(listOf("alpha:from-alpha"), alphaReceived)
        assertTrue(betaReceived.isEmpty())

        bus.execute(PublishSecondDeltaCommand("from-beta"))
        advanceUntilIdle()
        advanceVirtualTime(100)

        assertEquals(listOf("alpha:from-alpha"), alphaReceived)
        assertEquals(listOf("beta:from-beta"), betaReceived)
    }

    @Test
    fun `keeps a context's handler out of an event it does not subscribe to`() = runTest {
        val publisher = publisherContext()

        val alphaReceived = mutableListOf<String>()
        val betaReceived = mutableListOf<String>()
        val alphaStores = HandlerFactoryStoreCollection()
        val betaStores = HandlerFactoryStoreCollection()
        val alphaLocator = PersistingHandlerLocator(alphaStores)
        val betaLocator = PersistingHandlerLocator(betaStores)
        registerAlphaHandlerIn(alphaStores, alphaLocator, alphaReceived, "alpha")
        registerBetaHandlerIn(betaStores, betaLocator, betaReceived)

        val bus =
            MessageBus(
                appScope = backgroundScope,
                contexts =
                    listOf(
                        publisher,
                        BoundedContext(BoundedContextId("alpha"), alphaLocator),
                        BoundedContext(BoundedContextId("beta"), betaLocator),
                    ),
            )

        bus.execute(PublishAlphaCommand("event"))
        advanceUntilIdle()
        advanceVirtualTime(100)

        assertEquals(listOf("alpha:event"), alphaReceived)
        assertTrue(betaReceived.isEmpty())
    }

    @Test
    fun `delivers an event once to each context subscribing to it`() = runTest {
        val publisher = publisherContext()

        val received = mutableListOf<String>()
        // alpha and beta deliberately share one store here: the point of this test is two contexts
        // dispatching the same underlying handler factory via two independent mapper entries.
        val eventStores = HandlerFactoryStoreCollection()
        val alphaLocator = PersistingHandlerLocator(eventStores)
        val betaLocator = PersistingHandlerLocator(eventStores)
        registerAlphaHandlerIn(eventStores, alphaLocator, received, "alpha")
        betaLocator.integrationEventMapper.addEventHandlers(
            AlphaEvent::class,
            listOf(RecordingAlphaHandler::class),
        )

        val bus =
            MessageBus(
                appScope = backgroundScope,
                contexts =
                    listOf(
                        publisher,
                        BoundedContext(BoundedContextId("alpha"), alphaLocator),
                        BoundedContext(BoundedContextId("beta"), betaLocator),
                    ),
            )

        bus.execute(PublishAlphaCommand("event"))
        advanceUntilIdle()
        advanceVirtualTime(100)

        assertEquals(2, received.size)
    }

    @Test
    fun `emits to observers an event no context subscribes to`() = runTest {
        val publisher = publisherContext()
        val betaStores = HandlerFactoryStoreCollection()
        val betaLocator = PersistingHandlerLocator(betaStores)
        registerBetaHandlerIn(betaStores, betaLocator, mutableListOf())

        val bus =
            MessageBus(
                appScope = backgroundScope,
                contexts = listOf(publisher, BoundedContext(BoundedContextId("beta"), betaLocator)),
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
    fun `acknowledges an event no context subscribes to rather than retrying it forever`() =
        runTest {
            val store = MarkRecordingOutboxStore()
            val publisher = publisherContext()
            val betaStores = HandlerFactoryStoreCollection()
            val betaLocator = PersistingHandlerLocator(betaStores)
            registerBetaHandlerIn(betaStores, betaLocator, mutableListOf())

            val bus =
                MessageBus(
                    appScope = backgroundScope,
                    outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
                    contexts =
                        listOf(publisher, BoundedContext(BoundedContextId("beta"), betaLocator)),
                )
            bus.start()
            advanceVirtualTime(50)

            bus.execute(PublishAlphaCommand("unsubscribed"))
            advanceVirtualTime(150)

            assertEquals(1, store.markedPublished.size)
            assertTrue(store.fetchUnpublished(10).isEmpty())
        }

    @Test
    fun `leaves an entry unpublished when one context fails and redelivers to the rest on retry`() =
        runTest {
            val store = MarkRecordingOutboxStore()
            val publisher = publisherContext()

            val healthyReceived = mutableListOf<String>()
            val failedAttempts = mutableListOf<String>()
            val healthyStores = HandlerFactoryStoreCollection()
            val failingStores = HandlerFactoryStoreCollection()
            val healthyLocator = PersistingHandlerLocator(healthyStores)
            val failingLocator = PersistingHandlerLocator(failingStores)
            registerAlphaHandlerIn(healthyStores, healthyLocator, healthyReceived, "healthy")
            failingStores.eventStore.registerHandlers(
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
                    appScope = backgroundScope,
                    outbox = OutboxConfig(store = store, pollInterval = 100.milliseconds),
                    contexts =
                        listOf(
                            publisher,
                            BoundedContext(BoundedContextId("healthy"), healthyLocator),
                            BoundedContext(BoundedContextId("failing"), failingLocator),
                        ),
                )
            bus.start()

            bus.execute(PublishAlphaCommand("event"))
            advanceVirtualTime(400)

            // With no inbox, the whole entry stays unpublished, so the poller re-routes it to
            // *every*
            // context and the healthy one re-dispatches each cycle.
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

    // Idempotent, like a real store: re-marking an already-published id is a no-op.
    override suspend fun markPublished(ids: List<String>) {
        ids.forEach { if (it !in markedPublished) markedPublished.add(it) }
        entries.removeAll { it.id in ids }
    }
}
