package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.ErrorStrategy
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventHandler
import com.jimbroze.kbus.contracts.outbox.OutboxStore
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.core.infrastructure.outbox.InMemoryOutboxStore
import com.jimbroze.kbus.core.messages.event.publish.AutoPublishesFrom
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareHandler
import com.jimbroze.kbus.core.middleware.MiddlewareInvocationContext
import com.jimbroze.kbus.core.middleware.middleware.AutoPublishIntegrationEvents
import com.jimbroze.kbus.core.middleware.middleware.autoPublish
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventPublisher
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

class MessageBusOutboxTest {
    @Test
    fun commit_dispatches_the_imperative_event_via_the_outbox_exactly_once() = runTest {
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
                rootScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
            )
        bus.start()
        // Let the poller's immediate first (empty) pass settle into its long sleep.
        realDelay(50)

        bus.execute(OutboxImperativeCommand("via-imperative"))
        realDelay(150)

        assertEquals(listOf("via-imperative"), received)
        assertEquals(1, store.markedPublished.size)
    }

    @Test
    fun commit_dispatches_the_autopublished_event_via_the_outbox() = runTest {
        val store = RollbackSimulatingOutboxStore()
        val transactionManager = RollbackSimulatingTransactionManager(store)
        val stores = HandlerFactoryStoreCollection()
        val received = mutableListOf<String>()
        val locator = PersistingHandlerLocator(stores)
        registerDomainCommand(stores, locator, received)
        val middleware = AutoPublishIntegrationEvents(autoPublish(OutboxAutoPublishedEvent))

        val bus =
            MessageBus(
                locator,
                transactionManager = transactionManager,
                middlewares = listOf(middleware),
                rootScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
            )
        bus.start()
        realDelay(50)

        bus.execute(OutboxDomainCommand("via-autopublish"))
        realDelay(150)

        assertEquals(listOf("via-autopublish"), received)
        assertEquals(1, store.markedPublished.size)
        assertTrue(store.fetchUnpublished(10).isEmpty())
    }

    @Test
    fun rollback_discards_the_autopublished_event_so_nothing_is_saved_or_delivered() = runTest {
        val store = RollbackSimulatingOutboxStore()
        val transactionManager = RollbackSimulatingTransactionManager(store)
        val stores = HandlerFactoryStoreCollection()
        val received = mutableListOf<String>()
        val locator = PersistingHandlerLocator(stores)
        registerFailingDomainCommand(stores, locator, received)
        val middleware = AutoPublishIntegrationEvents(autoPublish(OutboxAutoPublishedEvent))

        val bus =
            MessageBus(
                locator,
                transactionManager = transactionManager,
                middlewares = listOf(middleware),
                rootScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
            )
        bus.start()
        realDelay(50)

        assertFailsWith<IllegalStateException> {
            bus.execute(OutboxDomainCommand("should-not-be-saved"))
        }
        realDelay(150)

        assertTrue(received.isEmpty())
        assertTrue(store.fetchUnpublished(10).isEmpty())
    }

    @Test
    fun rollback_discards_captured_events_so_nothing_is_dispatched() = runTest {
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
                rootScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
            )
        bus.start()
        realDelay(50)

        assertFailsWith<IllegalStateException> {
            bus.execute(OutboxImperativeCommand("should-not-be-dispatched"))
        }
        realDelay(150)

        assertTrue(received.isEmpty())
        assertTrue(store.fetchUnpublished(10).isEmpty())
    }

    /**
     * The post-commit drain is registered as `unitOfWork.addPostCommitWork { drain() }`, and
     * `drain` itself is an unawaited `outboxScope.launch` — so nothing about `bus.execute()`
     * returning happens-before the handler running, even though dispatch itself now awaits its
     * handlers (it just does so on `outboxScope`, not on the command's calling coroutine).
     * Asserting "not yet delivered" against wall-clock timing alone would race that launch. Gating
     * the handler on a [CompletableDeferred] makes the assertion deterministic: it cannot have
     * recorded anything until the test releases it.
     */
    @Test
    fun middleware_published_event_is_captured_by_the_outbox_and_not_delivered_before_commit() =
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
            locator.integrationEventMapper.addEventHandlers(
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
                    rootScope = backgroundScope,
                    outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
                )
            bus.start()
            realDelay(50)

            bus.execute(OutboxNoopCommand())

            assertTrue(received.isEmpty(), "Should not be delivered before commit")

            gate.complete(Unit)
            realDelay(150)

            assertEquals(listOf("via-middleware"), received)
        }

    @Test
    fun middleware_published_event_on_a_successful_command_is_saved_in_transaction_and_delivered() =
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
                    rootScope = backgroundScope,
                    outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
                )
            bus.start()
            realDelay(50)

            bus.execute(OutboxNoopCommand())
            realDelay(150)

            assertEquals(listOf("via-middleware-success"), received)
            assertEquals(1, store.markedPublished.size)
        }

    /**
     * Middleware wraps the whole command execution, strictly outside `transactionManager.execute()`
     * — but the outbox now defers its store write until `flush()`, which `CommandInvocationFactory`
     * registers as the *first* secondary work item on the command's unit of work. `flush()` only
     * runs once primary work completes without throwing, so a middleware-published event for a
     * command whose handler fails is never flushed to the store: it lived only in the outbox's
     * in-memory buffer, which is discarded along with the rest of that never-completed unit of
     * work. Nothing is ever staged, so there's nothing to roll back and nothing left for the poller
     * to find — the event is genuinely rollback-safe, not merely captured-but-undelivered.
     */
    @Test
    fun middleware_published_event_is_rolled_back_and_never_delivered_when_the_command_fails() =
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
                    rootScope = backgroundScope,
                    outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
                )
            bus.start()
            realDelay(50)

            assertFailsWith<IllegalStateException> { bus.execute(OutboxNoopCommand()) }
            realDelay(150)

            assertTrue(received.isEmpty(), "Never delivered: nothing was ever flushed to the store")
            assertTrue(store.fetchUnpublished(10).isEmpty(), "Rollback-safe: nothing staged")
        }

    @Test
    fun preexisting_unpublished_entries_are_delivered_on_the_pollers_first_pass() = runTest {
        val store = InMemoryOutboxStore()
        store.save(listOf(EventEnvelope("seeded-1", OutboxImperativeEvent("from-before-crash"))))
        val stores = HandlerFactoryStoreCollection()
        val received = mutableListOf<String>()
        val locator = PersistingHandlerLocator(stores)
        registerImperativeHandlerOnly(stores, locator, received)

        val bus =
            MessageBus(
                locator,
                rootScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
            )
        bus.start()
        realDelay(150)

        assertEquals(listOf("from-before-crash"), received)
    }

    @Test
    fun a_drain_failure_leaves_the_entry_for_the_poller_to_retry() = runTest {
        val store = InMemoryOutboxStore()
        val stores = HandlerFactoryStoreCollection()
        val attempts = mutableListOf<String>()
        val locator = PersistingHandlerLocator(stores)
        registerFlakyImperativeCommand(stores, locator, attempts)

        val bus =
            MessageBus(
                locator,
                rootScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 100.milliseconds),
            )
        bus.start()
        realDelay(30)

        bus.execute(OutboxFlakyCommand("flaky"))
        realDelay(250)

        assertTrue(attempts.size >= 2)
    }

    @Test
    fun poller_only_mode_delivers_without_a_drain() = runTest {
        val store = InMemoryOutboxStore()
        val stores = HandlerFactoryStoreCollection()
        val received = mutableListOf<String>()
        val locator = PersistingHandlerLocator(stores)
        registerImperativeCommand(stores, locator, received)

        val bus =
            MessageBus(
                locator,
                rootScope = backgroundScope,
                outbox =
                    OutboxConfig(
                        store = store,
                        pollInterval = 300.milliseconds,
                        opportunisticDrain = false,
                    ),
            )
        bus.start()
        realDelay(20)

        bus.execute(OutboxImperativeCommand("poller-only"))
        realDelay(20)
        assertTrue(received.isEmpty())

        realDelay(400)
        assertEquals(listOf("poller-only"), received)
    }

    @Test
    fun bus_without_outbox_config_dispatches_directly_as_before() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val received = mutableListOf<String>()
        val locator = PersistingHandlerLocator(stores)
        registerImperativeCommand(stores, locator, received)

        val bus = MessageBus(locator, rootScope = backgroundScope)

        bus.execute(OutboxImperativeCommand("no-outbox"))
        realDelay(100)

        assertContains(received, "no-outbox")
    }

    private suspend fun realDelay(millis: Long) =
        withContext(Dispatchers.Default) { delay(millis.milliseconds) }

    private fun registerImperativeCommand(
        stores: HandlerFactoryStoreCollection,
        locator: PersistingHandlerLocator,
        received: MutableList<String>,
    ) {
        stores.commandStore.registerHandlers(
            OutboxImperativeCommand::class,
            listOf(
                CommandHandlerFactory(OutboxImperativeCommandHandler::class) {
                    OutboxImperativeCommandHandler()
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
        locator.integrationEventMapper.addEventHandlers(
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
                    OutboxFailingCommandHandler()
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
        locator.integrationEventMapper.addEventHandlers(
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
        locator.integrationEventMapper.addEventHandlers(
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
                    OutboxFlakyCommandHandler()
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
        locator.integrationEventMapper.addEventHandlers(
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

private class OutboxImperativeCommandHandler :
    CommandHandler<OutboxImperativeCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: OutboxImperativeCommand): BusResult<Unit, MessageFailure> {
        publish(OutboxImperativeEvent(message.message))
        return BusResult.success(Unit)
    }
}

private class OutboxFailingCommandHandler :
    CommandHandler<OutboxImperativeCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: OutboxImperativeCommand): BusResult<Unit, MessageFailure> {
        publish(OutboxImperativeEvent(message.message))
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

private class OutboxAutoPublishedEvent(val name: String) : IntegrationEvent() {
    companion object : AutoPublishesFrom<OutboxDomainEvent> {
        override fun fromDomainEvent(event: OutboxDomainEvent) =
            OutboxAutoPublishedEvent(event.message)
    }
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

private class OutboxFlakyCommandHandler :
    CommandHandler<OutboxFlakyCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(message: OutboxFlakyCommand): BusResult<Unit, MessageFailure> {
        publish(OutboxFlakyEvent(message.message))
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
