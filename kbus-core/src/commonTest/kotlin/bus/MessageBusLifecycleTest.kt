package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    private suspend fun realDelay(millis: Long) =
        withContext(Dispatchers.Default) { delay(millis.milliseconds) }

    @Test
    fun lifecycle_aware_middleware_receives_scope_on_start_not_construction() = runTest {
        val middleware = CapturingLifecycleMiddleware()

        val bus = MessageBus(middlewares = listOf(middleware))
        assertNull(middleware.startContext)

        bus.start()

        assertNotNull(middleware.startContext)
        assertTrue(middleware.startContext!!.scope.isActive)
    }

    @Test
    fun multiple_lifecycle_middlewares_each_get_their_own_scope() = runTest {
        val middleware1 = CapturingLifecycleMiddleware("First")
        val middleware2 = CapturingLifecycleMiddleware("Second")

        val bus = MessageBus(middlewares = listOf(middleware1, middleware2))
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
    fun cancelling_root_scope_cancels_middleware_scopes() = runTest {
        val middleware = CapturingLifecycleMiddleware()
        val rootScope = CoroutineScope(Dispatchers.Default)

        val bus = MessageBus(middlewares = listOf(middleware), rootScope = rootScope)
        bus.start()

        assertTrue(middleware.startContext!!.scope.isActive)

        rootScope.cancel()

        realDelay(50)

        assertFalse(middleware.startContext!!.scope.isActive)
    }

    @Test
    fun nothing_polls_before_start() = runTest {
        val store = RecordingOutboxStore()
        val (locator, _) = busWithReturnCommand()

        MessageBus(
            locator,
            rootScope = backgroundScope,
            outbox = OutboxConfig(store = store, pollInterval = 10.milliseconds),
        )
        realDelay(50)

        assertTrue(store.fetchLimits.isEmpty())
    }

    @Test
    fun start_called_twice_launches_only_one_poller() = runTest {
        val store = RecordingOutboxStore()
        val (locator, _) = busWithReturnCommand()

        val bus =
            MessageBus(
                locator,
                rootScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
            )

        bus.start()
        bus.start()
        realDelay(50)

        assertEquals(1, store.fetchLimits.size)
    }

    @Test
    fun stop_halts_polling() = runTest {
        val store = RecordingOutboxStore()
        val (locator, _) = busWithReturnCommand()

        val bus =
            MessageBus(
                locator,
                rootScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 20.milliseconds),
            )

        bus.start()
        realDelay(50)
        val pollsBeforeStop = store.fetchLimits.size
        assertTrue(pollsBeforeStop > 0)

        bus.stop()
        realDelay(100)

        assertEquals(pollsBeforeStop, store.fetchLimits.size)
    }

    @Test
    fun stop_calls_onStop() = runTest {
        val middleware = CapturingLifecycleMiddleware()
        val bus = MessageBus(middlewares = listOf(middleware))

        bus.start()
        bus.stop()

        assertTrue(middleware.stopped)
    }

    @Test
    fun stop_awaitsASuspendingOnStopWithinTheGracePeriod() = runTest {
        val middleware = SuspendingStopMiddleware(onStopDelay = 50.milliseconds)
        val bus = MessageBus(middlewares = listOf(middleware), rootScope = backgroundScope)

        bus.start()
        bus.stop()

        assertTrue(middleware.stopCompleted, "stop() must await onStop, not just launch it")
    }

    /**
     * A middleware whose background work suspends inside `NonCancellable` can never be cancelled,
     * so an unbounded `rootJob.join()` would hang shutdown forever. The coroutine is deliberately
     * leaked; what matters is that `stop()` returns.
     */
    @Test
    fun stop_returnsWhenMiddlewareBackgroundWorkIgnoresCancellation() = runTest {
        val middleware = UncancellableWorkMiddleware()
        val bus = MessageBus(middlewares = listOf(middleware), rootScope = backgroundScope)

        bus.start()
        withContext(Dispatchers.Default) { middleware.started.await() }

        bus.stop(100.milliseconds)

        assertTrue(middleware.stopCompleted)
    }

    @Test
    fun stop_before_start_is_a_noop() = runTest {
        val middleware = CapturingLifecycleMiddleware()
        val bus = MessageBus(middlewares = listOf(middleware))

        bus.stop()

        assertFalse(middleware.stopped)
        assertNull(middleware.startContext)
    }

    @Test
    fun execute_throws_on_an_unstarted_bus_with_an_outbox() = runTest {
        val store = RecordingOutboxStore()
        val (locator, _) = busWithReturnCommand()

        val bus =
            MessageBus(locator, rootScope = backgroundScope, outbox = OutboxConfig(store = store))

        assertFailsWith<IllegalStateException> { bus.execute(ReturnCommand("test")) }
    }

    @Test
    fun execute_throws_on_an_unstarted_bus_with_lifecycle_aware_middleware() = runTest {
        val (locator, _) = busWithReturnCommand()
        val bus = MessageBus(locator, middlewares = listOf(CapturingLifecycleMiddleware()))

        assertFailsWith<IllegalStateException> { bus.execute(ReturnCommand("test")) }
    }

    @Test
    fun execute_still_works_unstarted_on_a_bus_with_neither_an_outbox_nor_lifecycle_middleware() =
        runTest {
            val (locator, _) = busWithReturnCommand()
            val bus = MessageBus(locator)

            val result = bus.execute(ReturnCommand("test"))

            assertTrue(result.isSuccess)
        }

    /**
     * The post-commit domain handler is launched detached (`eventDispatcherScope.launch`) rather
     * than awaited by `execute()`, so without the grace period `stop()`'s `rootJob.cancelAndJoin()`
     * would cancel it mid-flight and lose it silently. `withContext(Dispatchers.Default)` around
     * `stop()` puts a *real* dispatcher in the ambient coroutine context so its internal
     * `withTimeoutOrNull(gracePeriod)` measures real time — under `runTest`'s virtual clock alone
     * it would resolve near-instantly without giving the real background handler a chance to run.
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

        val bus = MessageBus(locator, rootScope = backgroundScope)
        bus.start()

        bus.execute(PublishLifecycleDomainEventCommand("event"))
        assertTrue(received.isEmpty(), "the handler is launched detached, not awaited by execute()")

        withContext(Dispatchers.Default) { bus.stop(500.milliseconds) }

        assertEquals(listOf("event"), received)
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

        val bus = MessageBus(locator, rootScope = backgroundScope)
        bus.start()

        bus.execute(PublishLifecycleDomainEventCommand("event"))
        withContext(Dispatchers.Default) { started.await() }

        val mark = TimeSource.Monotonic.markNow()
        withContext(Dispatchers.Default) { bus.stop(100.milliseconds) }
        val elapsed = mark.elapsedNow()

        assertTrue(elapsed < 3.seconds, "stop() must not block past the grace period: $elapsed")
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

private class SuspendingStopMiddleware(private val onStopDelay: Duration) :
    LifecycleAwareMiddleware {
    var stopCompleted = false
        private set

    override fun onStart(context: MiddlewareContext) = Unit

    override suspend fun onStop() {
        delay(onStopDelay)
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
