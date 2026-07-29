package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.CanPublishIntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventHandler
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.core.fixtures.CapturingLifecycleMiddleware
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.fixtures.ReturnCommand
import com.jimbroze.kbus.core.fixtures.ReturnCommandHandler
import com.jimbroze.kbus.core.middleware.LifecycleAwareMiddleware
import com.jimbroze.kbus.core.middleware.MiddlewareContext
import com.jimbroze.kbus.core.middleware.MiddlewareHandler
import com.jimbroze.kbus.core.middleware.MiddlewareInvocationContext
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.domain.event.DispatchTiming
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import com.jimbroze.kbus.domain.event.DomainEventPublisher
import com.jimbroze.kbus.testdoubles.advanceVirtualTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

class MessageBusLifecycleTest {
    private fun busWithReturnCommand():
        Pair<PersistingHandlerLocator, HandlerFactoryStoreCollection> {
        val stores = HandlerFactoryStoreCollection()
        stores.commandStore.registerHandlers(
            ReturnCommand::class,
            listOf(CommandHandlerFactory(ReturnCommandHandler::class) { ReturnCommandHandler() }),
        )
        return PersistingHandlerLocator(stores) to stores
    }

    @Test
    fun lifecycle_aware_middleware_receives_scope_on_start_not_construction() = runTest {
        val middleware = CapturingLifecycleMiddleware()

        val bus = MessageBus(middlewares = listOf(middleware), appScope = backgroundScope)
        assertNull(middleware.startContext)

        bus.start()

        assertNotNull(middleware.startContext)
        assertTrue(middleware.startContext!!.scope.isActive)
    }

    @Test
    fun multiple_lifecycle_middlewares_each_get_their_own_scope() = runTest {
        val middleware1 = CapturingLifecycleMiddleware("First")
        val middleware2 = CapturingLifecycleMiddleware("Second")

        val bus =
            MessageBus(middlewares = listOf(middleware1, middleware2), appScope = backgroundScope)
        bus.start()

        val scope1 = middleware1.startContext!!.scope
        val scope2 = middleware2.startContext!!.scope

        assertTrue(scope1.isActive)
        assertTrue(scope2.isActive)

        val name1 = scope1.coroutineContext[CoroutineName]?.name
        val name2 = scope2.coroutineContext[CoroutineName]?.name
        assertEquals("KBus-Middleware-CapturingLifecycleMiddleware", name1)
        assertEquals("KBus-Middleware-CapturingLifecycleMiddleware", name2)
    }

    @Test
    fun cancelling_the_app_scope_cancels_middleware_scopes() = runTest {
        val middleware = CapturingLifecycleMiddleware()
        // A *child* of backgroundScope — not a share of its context, which would reuse its Job and
        // make the cancel below tear down backgroundScope itself — so teardown cancels whatever the
        // bus launched even if the assertions below fail.
        val appScope =
            CoroutineScope(
                SupervisorJob(backgroundScope.coroutineContext[Job]) +
                    StandardTestDispatcher(testScheduler)
            )

        val bus = MessageBus(middlewares = listOf(middleware), appScope = appScope)
        bus.start()

        assertTrue(middleware.startContext!!.scope.isActive)

        appScope.cancel()

        advanceVirtualTime(50)

        assertFalse(middleware.startContext!!.scope.isActive)
    }

    @Test
    fun nothing_polls_before_start() = runTest {
        val store = RecordingOutboxStore()
        val (locator, _) = busWithReturnCommand()

        MessageBus(
            locator,
            appScope = backgroundScope,
            outbox = OutboxConfig(store = store, pollInterval = 10.milliseconds),
        )
        advanceVirtualTime(50)

        assertTrue(store.fetchLimits.isEmpty())
    }

    @Test
    fun start_called_twice_launches_only_one_poller() = runTest {
        val store = RecordingOutboxStore()
        val (locator, _) = busWithReturnCommand()

        val bus =
            MessageBus(
                locator,
                appScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
            )

        bus.start()
        bus.start()
        advanceVirtualTime(50)

        assertEquals(1, store.fetchLimits.size)
    }

    @Test
    fun stop_halts_polling() = runTest {
        val store = RecordingOutboxStore()
        val (locator, _) = busWithReturnCommand()

        val bus =
            MessageBus(
                locator,
                appScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 20.milliseconds),
            )

        bus.start()
        advanceVirtualTime(50)
        val pollsBeforeStop = store.fetchLimits.size
        assertTrue(pollsBeforeStop > 0)

        bus.stop()
        advanceVirtualTime(100)

        assertEquals(pollsBeforeStop, store.fetchLimits.size)
    }

    @Test
    fun stop_calls_onStop() = runTest {
        val middleware = CapturingLifecycleMiddleware()
        val bus = MessageBus(middlewares = listOf(middleware), appScope = backgroundScope)

        bus.start()
        bus.stop()

        assertTrue(middleware.stopped)
    }

    @Test
    fun stop_suspendsUntilASuspendingOnStopCompletes() = runTest {
        val middleware = GatedStopMiddleware()
        val bus = MessageBus(middlewares = listOf(middleware), appScope = backgroundScope)
        bus.start()

        val stopping = launch { bus.stop() }
        middleware.onStopEntered.await()

        assertFalse(stopping.isCompleted, "stop() must not return while onStop is still suspended")
        assertFalse(middleware.stopCompleted)

        middleware.gate.complete(Unit)
        stopping.join()

        assertTrue(middleware.stopCompleted)
    }

    /**
     * A middleware whose background work suspends inside `NonCancellable` can never be cancelled,
     * so an unbounded `rootJob.join()` would hang shutdown forever. The coroutine is deliberately
     * leaked; what matters is that `stop()` returns.
     */
    @Test
    fun stop_returnsWhenMiddlewareBackgroundWorkIgnoresCancellation() = runTest {
        val middleware = UncancellableWorkMiddleware()
        val bus = MessageBus(middlewares = listOf(middleware), appScope = backgroundScope)

        bus.start()
        middleware.started.await()

        bus.stop(100.milliseconds)

        assertTrue(middleware.stopCompleted)
    }

    @Test
    fun stop_before_start_is_a_noop() = runTest {
        val middleware = CapturingLifecycleMiddleware()
        val bus = MessageBus(middlewares = listOf(middleware), appScope = backgroundScope)

        bus.stop()

        assertFalse(middleware.stopped)
        assertNull(middleware.startContext)
    }

    @Test
    fun execute_throws_on_an_unstarted_bus_with_an_outbox() = runTest {
        val store = RecordingOutboxStore()
        val (locator, _) = busWithReturnCommand()

        val bus =
            MessageBus(locator, appScope = backgroundScope, outbox = OutboxConfig(store = store))

        assertFailsWith<IllegalStateException> { bus.execute(ReturnCommand("test")) }
    }

    @Test
    fun execute_throws_on_an_unstarted_bus_with_lifecycle_aware_middleware() = runTest {
        val (locator, _) = busWithReturnCommand()
        val bus =
            MessageBus(
                locator,
                middlewares = listOf(CapturingLifecycleMiddleware()),
                appScope = backgroundScope,
            )

        assertFailsWith<IllegalStateException> { bus.execute(ReturnCommand("test")) }
    }

    @Test
    fun execute_still_works_unstarted_on_a_bus_with_neither_an_outbox_nor_lifecycle_middleware() =
        runTest {
            val (locator, _) = busWithReturnCommand()
            val bus = MessageBus(locator, appScope = backgroundScope)

            val result = bus.execute(ReturnCommand("test"))

            assertTrue(result.isSuccess)
        }

    /**
     * The post-commit domain handler is launched detached (`eventDispatcherScope.launch`) rather
     * than awaited by `execute()`, so without the grace period `stop()`'s `rootJob.cancelAndJoin()`
     * would cancel it mid-flight and lose it silently.
     */
    @Test
    fun stop_awaitsAnInFlightDetachedPostCommitHandler_withinTheGracePeriod() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        stores.commandStore.registerHandlers(
            PublishLifecycleDomainEventCommand::class,
            listOf(
                CommandHandlerFactory(PublishLifecycleDomainEventCommandHandler::class) { deps ->
                    PublishLifecycleDomainEventCommandHandler(deps.domainEventPublisher)
                }
            ),
        )
        val received = mutableListOf<String>()
        stores.eventStore.registerHandlers(
            LifecycleDomainEvent::class,
            listOf(
                EventHandlerFactory(DelayingAfterTransactionHandler::class) {
                    DelayingAfterTransactionHandler(received, 100)
                }
            ),
        )
        locator.domainEventMapper.addDomainHandlers(
            LifecycleDomainEvent::class,
            listOf(DelayingAfterTransactionHandler::class),
        )

        val bus = MessageBus(locator, appScope = backgroundScope)
        bus.start()

        bus.execute(PublishLifecycleDomainEventCommand("event"))
        assertTrue(received.isEmpty(), "the handler is launched detached, not awaited by execute()")

        bus.stop(500.milliseconds)

        assertEquals(listOf("event"), received)
    }

    /**
     * Two detached hops: the post-commit domain handler is a child of `eventDispatcherScope` when
     * `stop()` is called, but the `FireAndForget` integration routing it publishes only becomes one
     * *during* the grace period. A one-shot snapshot of the scope's children would join the first
     * and cancel the second.
     */
    @Test
    fun stop_awaitsDetachedWorkLaunchedDuringTheGracePeriod() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        stores.commandStore.registerHandlers(
            PublishLifecycleDomainEventCommand::class,
            listOf(
                CommandHandlerFactory(PublishLifecycleDomainEventCommandHandler::class) { deps ->
                    PublishLifecycleDomainEventCommandHandler(deps.domainEventPublisher)
                }
            ),
        )
        stores.eventStore.registerHandlers(
            LifecycleDomainEvent::class,
            listOf(
                EventHandlerFactory(RepublishingAfterTransactionHandler::class) {
                    RepublishingAfterTransactionHandler(50)
                }
            ),
        )
        locator.domainEventMapper.addDomainHandlers(
            LifecycleDomainEvent::class,
            listOf(RepublishingAfterTransactionHandler::class),
        )
        val received = mutableListOf<String>()
        stores.eventStore.registerHandlers(
            LifecycleIntegrationEvent::class,
            listOf(
                EventHandlerFactory(DelayingLifecycleIntegrationHandler::class) {
                    DelayingLifecycleIntegrationHandler(received, 50)
                }
            ),
        )
        locator.integrationEventMapper.addEventHandlers(
            LifecycleIntegrationEvent::class,
            listOf(DelayingLifecycleIntegrationHandler::class),
        )

        val bus = MessageBus(locator, appScope = backgroundScope)
        bus.start()

        bus.execute(PublishLifecycleDomainEventCommand("event"))

        bus.stop(2.seconds)

        assertEquals(listOf("event"), received)
    }

    /** A bus whose every dispatch publishes its own successor, so detached work never runs out. */
    private fun respawningLocator(delayMs: Long): PersistingHandlerLocator {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        stores.commandStore.registerHandlers(
            PublishLifecycleDomainEventCommand::class,
            listOf(
                CommandHandlerFactory(PublishLifecycleDomainEventCommandHandler::class) { deps ->
                    PublishLifecycleDomainEventCommandHandler(deps.domainEventPublisher)
                }
            ),
        )
        stores.eventStore.registerHandlers(
            LifecycleDomainEvent::class,
            listOf(
                EventHandlerFactory(RepublishingAfterTransactionHandler::class) {
                    RepublishingAfterTransactionHandler(delayMs)
                }
            ),
        )
        locator.domainEventMapper.addDomainHandlers(
            LifecycleDomainEvent::class,
            listOf(RepublishingAfterTransactionHandler::class),
        )
        stores.eventStore.registerHandlers(
            LifecycleIntegrationEvent::class,
            listOf(
                EventHandlerFactory(SelfRepublishingIntegrationHandler::class) {
                    SelfRepublishingIntegrationHandler(delayMs)
                }
            ),
        )
        locator.integrationEventMapper.addEventHandlers(
            LifecycleIntegrationEvent::class,
            listOf(SelfRepublishingIntegrationHandler::class),
        )
        return locator
    }

    /**
     * The dispatch scope never has zero children, so the quiescence loop never sees an empty list —
     * only the grace period ends it. The successors delay, so the deadline is reached the ordinary
     * way; [stop_isBoundedWhenDetachedWorkRespawnsWithoutSuspending] covers the harder case.
     */
    @Test
    fun stop_isBoundedWhenDetachedWorkKeepsSpawningReplacements() = runTest {
        val bus = MessageBus(respawningLocator(1), appScope = backgroundScope)
        bus.start()

        bus.execute(PublishLifecycleDomainEventCommand("event"))

        val startedAt = testScheduler.currentTime
        bus.stop(100.milliseconds)
        val elapsed = (testScheduler.currentTime - startedAt).milliseconds

        assertTrue(
            elapsed <= 200.milliseconds,
            "stop() must not spin on endlessly respawned work: $elapsed",
        )
    }

    /**
     * Successors that respawn without ever suspending: `Job.join()` checking for cancellation even
     * when handed an already-completed job is the only thing that lets the grace period interrupt
     * the loop. Deliberately the one test here on a real clock — a virtual one is advanced by
     * suspension, so work that never suspends could never reach a virtual deadline.
     */
    @Test
    fun stop_isBoundedWhenDetachedWorkRespawnsWithoutSuspending() = runTest {
        // A real dispatcher is the point of this test, but the scope is still a *child* of
        // backgroundScope so endlessly respawning work cannot outlive the test if the assertions
        // below fail.
        val appScope =
            CoroutineScope(
                SupervisorJob(backgroundScope.coroutineContext[Job]) + Dispatchers.Default
            )
        val bus = MessageBus(respawningLocator(0), appScope = appScope)
        bus.start()

        bus.execute(PublishLifecycleDomainEventCommand("event"))

        val mark = TimeSource.Monotonic.markNow()
        withContext(Dispatchers.Default) { bus.stop(100.milliseconds) }
        val elapsed = mark.elapsedNow()

        appScope.cancel()

        assertTrue(
            elapsed < 3.seconds,
            "stop() must not spin on endlessly respawned work: $elapsed",
        )
    }

    @Test
    fun stop_doesNotBlockPastTheGracePeriodForAHandlerThatNeverCompletes() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        stores.commandStore.registerHandlers(
            PublishLifecycleDomainEventCommand::class,
            listOf(
                CommandHandlerFactory(PublishLifecycleDomainEventCommandHandler::class) { deps ->
                    PublishLifecycleDomainEventCommandHandler(deps.domainEventPublisher)
                }
            ),
        )
        val started = CompletableDeferred<Unit>()
        stores.eventStore.registerHandlers(
            LifecycleDomainEvent::class,
            listOf(
                EventHandlerFactory(NeverCompletingAfterTransactionHandler::class) {
                    NeverCompletingAfterTransactionHandler(started)
                }
            ),
        )
        locator.domainEventMapper.addDomainHandlers(
            LifecycleDomainEvent::class,
            listOf(NeverCompletingAfterTransactionHandler::class),
        )

        val bus = MessageBus(locator, appScope = backgroundScope)
        bus.start()

        bus.execute(PublishLifecycleDomainEventCommand("event"))
        started.await()

        val startedAt = testScheduler.currentTime
        bus.stop(100.milliseconds)
        val elapsed = (testScheduler.currentTime - startedAt).milliseconds

        assertTrue(
            elapsed <= 200.milliseconds,
            "stop() must not block past the grace period: $elapsed",
        )
    }
}

private class LifecycleDomainEvent(val message: String) : DomainEvent()

private class PublishLifecycleDomainEventCommand(val message: String) :
    Command<BusResult<Unit, MessageFailure>>()

private class PublishLifecycleDomainEventCommandHandler(
    private val domainEventPublisher: DomainEventPublisher
) : CommandHandler<PublishLifecycleDomainEventCommand, BusResult<Unit, MessageFailure>>() {
    override suspend fun handle(
        message: PublishLifecycleDomainEventCommand
    ): BusResult<Unit, MessageFailure> {
        domainEventPublisher.publish(LifecycleDomainEvent(message.message))
        return BusResult.success(Unit)
    }
}

/** Default errorStrategy (FireAndForget): detached via `eventDispatcherScope.launch`. */
private class DelayingAfterTransactionHandler(
    private val received: MutableList<String>,
    private val delayMs: Long,
) : DomainEventHandler<LifecycleDomainEvent>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: LifecycleDomainEvent) {
        delay(delayMs.milliseconds)
        received.add(message.message)
    }
}

/** Signals [started] then suspends forever, to exercise stop()'s grace-period cutoff. */
private class NeverCompletingAfterTransactionHandler(
    private val started: CompletableDeferred<Unit>
) : DomainEventHandler<LifecycleDomainEvent>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: LifecycleDomainEvent) {
        started.complete(Unit)
        awaitCancellation()
    }
}

/** Suspends inside [onStop] until [gate] completes, so a test can observe stop() waiting. */
private class GatedStopMiddleware : LifecycleAwareMiddleware {
    val onStopEntered = CompletableDeferred<Unit>()
    val gate = CompletableDeferred<Unit>()

    var stopCompleted = false
        private set

    override fun onStart(context: MiddlewareContext) = Unit

    override suspend fun onStop() {
        onStopEntered.complete(Unit)
        gate.await()
        stopCompleted = true
    }

    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        context: MiddlewareInvocationContext,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult = nextMiddleware(message)
}

/** Launches background work that cannot be cancelled, so `rootJob.join()` can never complete. */
private class UncancellableWorkMiddleware : LifecycleAwareMiddleware {
    val started = CompletableDeferred<Unit>()

    var stopCompleted = false
        private set

    override fun onStart(context: MiddlewareContext) {
        context.scope.launch {
            withContext(NonCancellable) {
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }
    }

    override suspend fun onStop() {
        stopCompleted = true
    }

    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        context: MiddlewareInvocationContext,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult = nextMiddleware(message)
}

private class LifecycleIntegrationEvent(val message: String) : IntegrationEvent()

/** Publishes a further detached hop — the integration routing — from inside a detached handler. */
private class RepublishingAfterTransactionHandler(private val delayMs: Long) :
    DomainEventHandler<LifecycleDomainEvent>() {
    override val dispatchTiming = DispatchTiming.AfterTransaction

    override suspend fun handle(message: LifecycleDomainEvent) {
        delay(delayMs.milliseconds)
        publish(LifecycleIntegrationEvent(message.message))
    }
}

private class DelayingLifecycleIntegrationHandler(
    private val received: MutableList<String>,
    private val delayMs: Long,
) : IntegrationEventHandler<LifecycleIntegrationEvent> {
    override suspend fun handle(message: LifecycleIntegrationEvent) {
        delay(delayMs.milliseconds)
        received.add(message.message)
    }
}

/** Publishes its own successor on every dispatch, so detached work never runs out. */
private class SelfRepublishingIntegrationHandler(private val delayMs: Long) :
    CanPublishIntegrationEvent(), IntegrationEventHandler<LifecycleIntegrationEvent> {
    override suspend fun handle(message: LifecycleIntegrationEvent) {
        delay(delayMs.milliseconds)
        publish(LifecycleIntegrationEvent(message.message))
    }
}
