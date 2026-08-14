package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.api.common.Message
import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.api.messages.event.ErrorStrategy
import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import com.jimbroze.kbus.api.messages.event.IntegrationEventHandler
import com.jimbroze.kbus.api.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.api.result.MessageFailure
import com.jimbroze.kbus.api.uow.TransactionManager
import com.jimbroze.kbus.application.messages.event.IntegrationEventMapper
import com.jimbroze.kbus.core.middleware.AutoPublishIntegrationEvents
import com.jimbroze.kbus.core.middleware.autoPublish
import com.jimbroze.kbus.core.middleware.infrastructure.Middleware
import com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareHandler
import com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareInvocationContext
import com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareScope
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventPublisher
import com.jimbroze.kbus.infrastructure.event.EventEnvelope
import com.jimbroze.kbus.infrastructure.outbox.OutboxStore
import com.jimbroze.kbus.infrastructure.outbox.adapters.InMemoryOutboxStore
import com.jimbroze.kbus.testdoubles.advanceVirtualTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

class MessageBusOutboxTest {
    @Test
    fun `delivers an explicitly published event through the outbox exactly once`() = runTest {
        val store = RollbackSimulatingOutboxStore()
        val transactionManager = RollbackSimulatingTransactionManager(store)
        val stores = HandlerFactoryStoreCollection()
        val received = mutableListOf<String>()
        val locator = PersistingHandlerLocator(stores)
        registerImperativeCommand(stores, locator, received)

        val bus =
            MessageBus(
                locator,
                transactionManager = transactionManager,
                appScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
            )
        bus.start()
        // Let the poller's immediate first (empty) pass settle into its long sleep.
        advanceVirtualTime(50)

        bus.execute(OutboxImperativeCommand("via-imperative"))
        advanceVirtualTime(150)

        assertEquals(listOf("via-imperative"), received)
        assertEquals(1, store.markedPublished.size)
    }

    @Test
    fun `delivers an auto-published event through the outbox`() = runTest {
        val store = RollbackSimulatingOutboxStore()
        val transactionManager = RollbackSimulatingTransactionManager(store)
        val stores = HandlerFactoryStoreCollection()
        val received = mutableListOf<String>()
        val locator = PersistingHandlerLocator(stores)
        registerDomainCommand(stores, locator, received)
        val middleware = AutoPublishIntegrationEvents(autoPublish(OutboxAutoPublishedEventMapper))

        val bus =
            MessageBus(
                locator,
                transactionManager = transactionManager,
                middlewares = listOf(middleware),
                appScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
            )
        bus.start()
        advanceVirtualTime(50)

        bus.execute(OutboxDomainCommand("via-autopublish"))
        advanceVirtualTime(150)

        assertEquals(listOf("via-autopublish"), received)
        assertEquals(1, store.markedPublished.size)
        assertTrue(store.fetchUnpublished(10).isEmpty())
    }

    @Test
    fun `saves and delivers no auto-published event when the transaction rolls back`() = runTest {
        val store = RollbackSimulatingOutboxStore()
        val transactionManager = RollbackSimulatingTransactionManager(store)
        val stores = HandlerFactoryStoreCollection()
        val received = mutableListOf<String>()
        val locator = PersistingHandlerLocator(stores)
        registerFailingDomainCommand(stores, locator, received)
        val middleware = AutoPublishIntegrationEvents(autoPublish(OutboxAutoPublishedEventMapper))

        val bus =
            MessageBus(
                locator,
                transactionManager = transactionManager,
                middlewares = listOf(middleware),
                appScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
            )
        bus.start()
        advanceVirtualTime(50)

        assertFailsWith<IllegalStateException> {
            bus.execute(OutboxDomainCommand("should-not-be-saved"))
        }
        advanceVirtualTime(150)

        assertTrue(received.isEmpty())
        assertTrue(store.fetchUnpublished(10).isEmpty())
    }

    @Test
    fun `delivers no captured event when the transaction rolls back`() = runTest {
        val store = RollbackSimulatingOutboxStore()
        val transactionManager = RollbackSimulatingTransactionManager(store)
        val stores = HandlerFactoryStoreCollection()
        val received = mutableListOf<String>()
        val locator = PersistingHandlerLocator(stores)
        registerFailingImperativeCommand(stores, locator, received)

        val bus =
            MessageBus(
                locator,
                transactionManager = transactionManager,
                appScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
            )
        bus.start()
        advanceVirtualTime(50)

        assertFailsWith<IllegalStateException> {
            bus.execute(OutboxImperativeCommand("should-not-be-dispatched"))
        }
        advanceVirtualTime(150)

        assertTrue(received.isEmpty())
        assertTrue(store.fetchUnpublished(10).isEmpty())
    }

    /**
     * Nothing about `bus.execute()` returning happens-before the drained handler running, so
     * asserting "not yet delivered" on timing alone would race. Gating the handler makes it
     * deterministic: it cannot record anything until the test releases it.
     */
    @Test
    fun `holds an event published by middleware in the outbox until the transaction commits`() =
        runTest {
            val store = InMemoryOutboxStore()
            val stores = HandlerFactoryStoreCollection()
            val received = mutableListOf<String>()
            val gate = CompletableDeferred<Unit>()
            val locator = PersistingHandlerLocator(stores)
            stores.eventStore.registerHandlers(
                OutboxImperativeEvent::class,
                listOf(
                    EventHandlerFactory(GatedOutboxEventHandler::class) {
                        GatedOutboxEventHandler(received, gate)
                    }
                ),
            )
            locator.integrationEventRegistrar.addEventHandlers(
                OutboxImperativeEvent::class,
                listOf(GatedOutboxEventHandler::class),
            )
            stores.commandStore.registerHandlers(
                OutboxNoopCommand::class,
                listOf(
                    CommandHandlerFactory(OutboxNoopCommandHandler::class) {
                        OutboxNoopCommandHandler()
                    }
                ),
            )
            val middleware = PublishingViaContextMiddleware(OutboxImperativeEvent("via-middleware"))

            val bus =
                MessageBus(
                    locator,
                    middlewares = listOf(middleware),
                    appScope = backgroundScope,
                    outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
                )
            bus.start()
            advanceVirtualTime(50)

            bus.execute(OutboxNoopCommand())

            assertTrue(received.isEmpty(), "Should not be delivered before commit")

            gate.complete(Unit)
            advanceVirtualTime(150)

            assertEquals(listOf("via-middleware"), received)
        }

    /**
     * The default FireAndForget strategy gets the same ack timing as FailFast: dispatch awaits
     * every handler before the poller (or drain) marks an entry published.
     */
    @Test
    fun `marks an entry published only once its handler has completed`() = runTest {
        val store = InMemoryOutboxStore()
        val stores = HandlerFactoryStoreCollection()
        val received = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val locator = PersistingHandlerLocator(stores)
        stores.eventStore.registerHandlers(
            OutboxImperativeEvent::class,
            listOf(
                EventHandlerFactory(GatedOutboxEventHandler::class) {
                    GatedOutboxEventHandler(received, gate)
                }
            ),
        )
        locator.integrationEventRegistrar.addEventHandlers(
            OutboxImperativeEvent::class,
            listOf(GatedOutboxEventHandler::class),
        )
        stores.commandStore.registerHandlers(
            OutboxImperativeCommand::class,
            listOf(
                CommandHandlerFactory(OutboxImperativeCommandHandler::class) {
                    OutboxImperativeCommandHandler(it.integrationEventPublisher)
                }
            ),
        )

        val bus =
            MessageBus(
                locator,
                appScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
            )
        bus.start()
        advanceVirtualTime(50)

        bus.execute(OutboxImperativeCommand("event"))
        advanceVirtualTime(100)

        assertTrue(received.isEmpty(), "the handler is still suspended on the gate")
        assertTrue(
            store.fetchUnpublished(10).isNotEmpty(),
            "must not be marked published while the handler is still running",
        )

        gate.complete(Unit)
        advanceVirtualTime(150)

        assertEquals(listOf("event"), received)
        assertTrue(store.fetchUnpublished(10).isEmpty())
    }

    @Test
    fun `saves an event published by middleware in the transaction and delivers it after commit`() =
        runTest {
            val store = RollbackSimulatingOutboxStore()
            val transactionManager = RollbackSimulatingTransactionManager(store)
            val stores = HandlerFactoryStoreCollection()
            val received = mutableListOf<String>()
            val locator = PersistingHandlerLocator(stores)
            registerImperativeHandlerOnly(stores, locator, received)
            stores.commandStore.registerHandlers(
                OutboxNoopCommand::class,
                listOf(
                    CommandHandlerFactory(OutboxNoopCommandHandler::class) {
                        OutboxNoopCommandHandler()
                    }
                ),
            )
            val middleware =
                PublishingViaContextMiddleware(OutboxImperativeEvent("via-middleware-success"))

            val bus =
                MessageBus(
                    locator,
                    transactionManager = transactionManager,
                    middlewares = listOf(middleware),
                    appScope = backgroundScope,
                    outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
                )
            bus.start()
            advanceVirtualTime(50)

            bus.execute(OutboxNoopCommand())
            advanceVirtualTime(150)

            assertEquals(listOf("via-middleware-success"), received)
            assertEquals(1, store.markedPublished.size)
        }

    /**
     * A middleware publishes outside the transaction, but the store write is deferred to a flush
     * that only runs once primary work has completed. A failing handler therefore stages nothing:
     * the event is rollback-safe, not merely captured-but-undelivered.
     */
    @Test
    fun `never delivers an event published by middleware when the command fails`() = runTest {
        val store = RollbackSimulatingOutboxStore()
        val transactionManager = RollbackSimulatingTransactionManager(store)
        val stores = HandlerFactoryStoreCollection()
        val received = mutableListOf<String>()
        val locator = PersistingHandlerLocator(stores)
        registerImperativeHandlerOnly(stores, locator, received)
        stores.commandStore.registerHandlers(
            OutboxNoopCommand::class,
            listOf(
                CommandHandlerFactory(OutboxFailingNoopCommandHandler::class) {
                    OutboxFailingNoopCommandHandler()
                }
            ),
        )
        val middleware =
            PublishingViaContextMiddleware(OutboxImperativeEvent("via-middleware-failing"))

        val bus =
            MessageBus(
                locator,
                transactionManager = transactionManager,
                middlewares = listOf(middleware),
                appScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
            )
        bus.start()
        advanceVirtualTime(50)

        assertFailsWith<IllegalStateException> { bus.execute(OutboxNoopCommand()) }
        advanceVirtualTime(150)

        assertTrue(received.isEmpty(), "Never delivered: nothing was ever flushed to the store")
        assertTrue(store.fetchUnpublished(10).isEmpty(), "Rollback-safe: nothing staged")
    }

    @Test
    fun `delivers entries left unpublished on the poller's first pass`() = runTest {
        val store = InMemoryOutboxStore()
        store.save(listOf(EventEnvelope("seeded-1", OutboxImperativeEvent("from-before-crash"))))
        val stores = HandlerFactoryStoreCollection()
        val received = mutableListOf<String>()
        val locator = PersistingHandlerLocator(stores)
        registerImperativeHandlerOnly(stores, locator, received)

        val bus =
            MessageBus(
                locator,
                appScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
            )
        bus.start()
        advanceVirtualTime(150)

        assertEquals(listOf("from-before-crash"), received)
    }

    @Test
    fun `leaves an entry for the poller to retry when the drain fails`() = runTest {
        val store = InMemoryOutboxStore()
        val stores = HandlerFactoryStoreCollection()
        val attempts = mutableListOf<String>()
        val locator = PersistingHandlerLocator(stores)
        registerFlakyImperativeCommand(stores, locator, attempts)

        val bus =
            MessageBus(
                locator,
                appScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 100.milliseconds),
            )
        bus.start()
        advanceVirtualTime(30)

        bus.execute(OutboxFlakyCommand("flaky"))
        advanceVirtualTime(250)

        assertTrue(attempts.size >= 2)
    }

    @Test
    fun `delivers entries with the poller alone when the drain is disabled`() = runTest {
        val store = InMemoryOutboxStore()
        val stores = HandlerFactoryStoreCollection()
        val received = mutableListOf<String>()
        val locator = PersistingHandlerLocator(stores)
        registerImperativeCommand(stores, locator, received)

        val bus =
            MessageBus(
                locator,
                appScope = backgroundScope,
                outbox =
                    OutboxConfig(
                        store = store,
                        pollInterval = 300.milliseconds,
                        opportunisticDrain = false,
                    ),
            )
        bus.start()
        advanceVirtualTime(20)

        bus.execute(OutboxImperativeCommand("poller-only"))
        advanceVirtualTime(20)
        assertTrue(received.isEmpty())

        advanceVirtualTime(400)
        assertEquals(listOf("poller-only"), received)
    }

    @Test
    fun `delivers events directly when no outbox is configured`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val received = mutableListOf<String>()
        val locator = PersistingHandlerLocator(stores)
        registerImperativeCommand(stores, locator, received)

        val bus = MessageBus(locator, appScope = backgroundScope)

        bus.execute(OutboxImperativeCommand("no-outbox"))
        advanceVirtualTime(100)

        assertContains(received, "no-outbox")
    }

    private fun registerImperativeCommand(
        stores: HandlerFactoryStoreCollection,
        locator: PersistingHandlerLocator,
        received: MutableList<String>,
    ) {
        stores.commandStore.registerHandlers(
            OutboxImperativeCommand::class,
            listOf(
                CommandHandlerFactory(OutboxImperativeCommandHandler::class) {
                    OutboxImperativeCommandHandler(it.integrationEventPublisher)
                }
            ),
        )
        registerImperativeHandlerOnly(stores, locator, received)
    }

    private fun registerImperativeHandlerOnly(
        stores: HandlerFactoryStoreCollection,
        locator: PersistingHandlerLocator,
        received: MutableList<String>,
    ) {
        stores.eventStore.registerHandlers(
            OutboxImperativeEvent::class,
            listOf(
                EventHandlerFactory(RecordingOutboxEventHandler::class) {
                    RecordingOutboxEventHandler(received)
                }
            ),
        )
        locator.integrationEventRegistrar.addEventHandlers(
            OutboxImperativeEvent::class,
            listOf(RecordingOutboxEventHandler::class),
        )
    }

    private fun registerFailingImperativeCommand(
        stores: HandlerFactoryStoreCollection,
        locator: PersistingHandlerLocator,
        received: MutableList<String>,
    ) {
        stores.commandStore.registerHandlers(
            OutboxImperativeCommand::class,
            listOf(
                CommandHandlerFactory(OutboxFailingCommandHandler::class) {
                    OutboxFailingCommandHandler(it.integrationEventPublisher)
                }
            ),
        )
        registerImperativeHandlerOnly(stores, locator, received)
    }

    private fun registerDomainCommand(
        stores: HandlerFactoryStoreCollection,
        locator: PersistingHandlerLocator,
        received: MutableList<String>,
    ) {
        stores.commandStore.registerHandlers(
            OutboxDomainCommand::class,
            listOf(
                CommandHandlerFactory(OutboxDomainCommandHandler::class) { deps ->
                    OutboxDomainCommandHandler(deps.domainEventPublisher)
                }
            ),
        )
        stores.eventStore.registerHandlers(
            OutboxAutoPublishedEvent::class,
            listOf(
                EventHandlerFactory(RecordingOutboxAutoPublishedEventHandler::class) {
                    RecordingOutboxAutoPublishedEventHandler(received)
                }
            ),
        )
        locator.integrationEventRegistrar.addEventHandlers(
            OutboxAutoPublishedEvent::class,
            listOf(RecordingOutboxAutoPublishedEventHandler::class),
        )
    }

    private fun registerFailingDomainCommand(
        stores: HandlerFactoryStoreCollection,
        locator: PersistingHandlerLocator,
        received: MutableList<String>,
    ) {
        stores.commandStore.registerHandlers(
            OutboxDomainCommand::class,
            listOf(
                CommandHandlerFactory(OutboxFailingDomainCommandHandler::class) { deps ->
                    OutboxFailingDomainCommandHandler(deps.domainEventPublisher)
                }
            ),
        )
        stores.eventStore.registerHandlers(
            OutboxAutoPublishedEvent::class,
            listOf(
                EventHandlerFactory(RecordingOutboxAutoPublishedEventHandler::class) {
                    RecordingOutboxAutoPublishedEventHandler(received)
                }
            ),
        )
        locator.integrationEventRegistrar.addEventHandlers(
            OutboxAutoPublishedEvent::class,
            listOf(RecordingOutboxAutoPublishedEventHandler::class),
        )
    }

    private fun registerFlakyImperativeCommand(
        stores: HandlerFactoryStoreCollection,
        locator: PersistingHandlerLocator,
        attempts: MutableList<String>,
    ) {
        stores.commandStore.registerHandlers(
            OutboxFlakyCommand::class,
            listOf(
                CommandHandlerFactory(OutboxFlakyCommandHandler::class) {
                    OutboxFlakyCommandHandler(it.integrationEventPublisher)
                }
            ),
        )
        stores.eventStore.registerHandlers(
            OutboxFlakyEvent::class,
            listOf(
                EventHandlerFactory(AlwaysThrowingOutboxEventHandler::class) {
                    AlwaysThrowingOutboxEventHandler(attempts)
                }
            ),
        )
        locator.integrationEventRegistrar.addEventHandlers(
            OutboxFlakyEvent::class,
            listOf(AlwaysThrowingOutboxEventHandler::class),
        )
    }
}

// --- Test doubles ---

private class OutboxImperativeEvent(val name: String) : IntegrationEvent()

private class OutboxNoopCommand : Command<BusResult<Unit, MessageFailure>>()

private class OutboxNoopCommandHandler :
    CommandHandler<OutboxNoopCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: OutboxNoopCommand): BusResult<Unit, MessageFailure> {
        return BusResult.success(Unit)
    }
}

private class OutboxFailingNoopCommandHandler :
    CommandHandler<OutboxNoopCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: OutboxNoopCommand): BusResult<Unit, MessageFailure> {
        error("handler failed")
    }
}

/**
 * Publishes [event] via the middleware context while handling a command, before delegating to the
 * next middleware. Guarded to commands only: this middleware is on the bus-wide chain, so it also
 * wraps the integration-event dispatch that the outbox drain triggers, and would otherwise publish
 * again on every drain, recursively.
 */
private class PublishingViaContextMiddleware(private val event: IntegrationEvent) : Middleware {
    override val scope = MiddlewareScope.EntryPointOnly

    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        context: MiddlewareInvocationContext,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        if (message is Command<*>) {
            context.integrationEventPublisher.publish(listOf(event))
        }
        return nextMiddleware(message)
    }
}

private class OutboxImperativeCommand(val message: String) :
    Command<BusResult<Unit, MessageFailure>>()

private class OutboxImperativeCommandHandler(
    private val integrationEventPublisher: IntegrationEventPublisher
) : CommandHandler<OutboxImperativeCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: OutboxImperativeCommand): BusResult<Unit, MessageFailure> {
        integrationEventPublisher.publish(listOf(OutboxImperativeEvent(message.message)))
        return BusResult.success(Unit)
    }
}

private class OutboxFailingCommandHandler(
    private val integrationEventPublisher: IntegrationEventPublisher
) : CommandHandler<OutboxImperativeCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: OutboxImperativeCommand): BusResult<Unit, MessageFailure> {
        integrationEventPublisher.publish(listOf(OutboxImperativeEvent(message.message)))
        error("handler failed after dispatching")
    }
}

private class RecordingOutboxEventHandler(private val received: MutableList<String>) :
    IntegrationEventHandler<OutboxImperativeEvent> {
    override suspend fun handle(message: OutboxImperativeEvent) {
        received.add(message.name)
    }
}

/** Suspends until [gate] completes, so a test can deterministically observe pre-delivery state. */
private class GatedOutboxEventHandler(
    private val received: MutableList<String>,
    private val gate: CompletableDeferred<Unit>,
) : IntegrationEventHandler<OutboxImperativeEvent> {
    override suspend fun handle(message: OutboxImperativeEvent) {
        gate.await()
        received.add(message.name)
    }
}

private class OutboxDomainEvent(val message: String) : DomainEvent()

private class OutboxAutoPublishedEvent(val name: String) : IntegrationEvent()

private object OutboxAutoPublishedEventMapper : IntegrationEventMapper<OutboxDomainEvent> {
    override fun fromDomainEvent(event: OutboxDomainEvent) = OutboxAutoPublishedEvent(event.message)
}

private class OutboxDomainCommand(val message: String) : Command<BusResult<Unit, MessageFailure>>()

private class OutboxDomainCommandHandler(private val domainEventPublisher: DomainEventPublisher) :
    CommandHandler<OutboxDomainCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: OutboxDomainCommand): BusResult<Unit, MessageFailure> {
        domainEventPublisher.publish(OutboxDomainEvent(message.message))
        return BusResult.success(Unit)
    }
}

private class OutboxFailingDomainCommandHandler(
    private val domainEventPublisher: DomainEventPublisher
) : CommandHandler<OutboxDomainCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: OutboxDomainCommand): BusResult<Unit, MessageFailure> {
        domainEventPublisher.publish(OutboxDomainEvent(message.message))
        error("handler failed after publishing domain event")
    }
}

private class RecordingOutboxAutoPublishedEventHandler(private val received: MutableList<String>) :
    IntegrationEventHandler<OutboxAutoPublishedEvent> {
    override suspend fun handle(message: OutboxAutoPublishedEvent) {
        received.add(message.name)
    }
}

/** FailFast so a throwing handler propagates synchronously back through publish(). */
private class OutboxFlakyEvent(val name: String) : IntegrationEvent() {
    override val errorStrategy = ErrorStrategy.FailFast
}

private class OutboxFlakyCommand(val message: String) : Command<BusResult<Unit, MessageFailure>>()

private class OutboxFlakyCommandHandler(
    private val integrationEventPublisher: IntegrationEventPublisher
) : CommandHandler<OutboxFlakyCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: OutboxFlakyCommand): BusResult<Unit, MessageFailure> {
        integrationEventPublisher.publish(listOf(OutboxFlakyEvent(message.message)))
        return BusResult.success(Unit)
    }
}

private class AlwaysThrowingOutboxEventHandler(private val attempts: MutableList<String>) :
    IntegrationEventHandler<OutboxFlakyEvent> {
    override suspend fun handle(message: OutboxFlakyEvent) {
        attempts.add(message.name)
        error("delivery always fails")
    }
}

private class RollbackSimulatingOutboxStore : OutboxStore {
    private val committed = mutableListOf<EventEnvelope>()
    private var staged: MutableList<EventEnvelope>? = null
    val markedPublished = mutableListOf<String>()

    fun beginTransaction() {
        staged = mutableListOf()
    }

    fun commitTransaction() {
        staged?.let { committed.addAll(it) }
        staged = null
    }

    fun rollbackTransaction() {
        staged = null
    }

    override suspend fun save(entries: List<EventEnvelope>) {
        val target = staged
        if (target != null) target.addAll(entries) else committed.addAll(entries)
    }

    override suspend fun fetchUnpublished(limit: Int): List<EventEnvelope> {
        val published = markedPublished.toSet()
        return committed.filterNot { it.id in published }.take(limit)
    }

    override suspend fun markPublished(ids: List<String>) {
        markedPublished.addAll(ids)
    }
}

private class RollbackSimulatingTransactionManager(
    private val store: RollbackSimulatingOutboxStore
) : TransactionManager {
    override suspend fun <TResult> execute(block: suspend () -> TResult): TResult {
        store.beginTransaction()
        return try {
            val result = block()
            store.commitTransaction()
            result
        } catch (e: Throwable) {
            store.rollbackTransaction()
            throw e
        }
    }
}
