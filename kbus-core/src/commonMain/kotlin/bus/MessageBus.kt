package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.contracts.common.MissingHandlerException
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.messages.command.CommandExecutor
import com.jimbroze.kbus.core.messages.command.CommandInvocationFactory
import com.jimbroze.kbus.core.messages.command.DefaultCommandDependenciesFactory
import com.jimbroze.kbus.core.messages.event.dispatch.EventDispatcher
import com.jimbroze.kbus.core.messages.event.publish.DirectPublisher
import com.jimbroze.kbus.core.messages.event.publish.IntegrationEventPublisherFactory
import com.jimbroze.kbus.core.messages.event.routing.EventRouter
import com.jimbroze.kbus.core.messages.query.QueryFetcher
import com.jimbroze.kbus.core.middleware.BusMiddlewareContext
import com.jimbroze.kbus.core.middleware.LifecycleAwareMiddleware
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareInvocationContextFactory
import com.jimbroze.kbus.core.module.BoundedContext
import com.jimbroze.kbus.core.module.BoundedContextId
import com.jimbroze.kbus.core.module.LocatorSubscriptions
import com.jimbroze.kbus.core.module.inbox.InboxConfig
import com.jimbroze.kbus.core.module.inbox.InboxCoordinator
import com.jimbroze.kbus.core.registry.HandlerLocator
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.uow.DefaultUnitOfWorkFactory
import com.jimbroze.kbus.core.uow.EmptyTransactionManager
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.core.uow.OutboxCoordinator
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeoutOrNull

private val DEFAULT_STOP_GRACE_PERIOD = 10.seconds

interface IMessageBus {
    suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
        command: TCommand
    ): TResult

    suspend fun <TQuery : Query<TResult>, TResult : KBusResult> fetch(query: TQuery): TResult
}

@Suppress("LongParameterList")
abstract class BaseMessageBus(
    protected val handlerLocator: HandlerLocator,
    transactionManager: TransactionManager?,
    protected val middlewares: List<Middleware>,
    appScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    outbox: OutboxConfig? = null,
    contexts: Map<BoundedContextId, HandlerLocator> = emptyMap(),
    inbox: InboxConfig? = null,
) : IMessageBus {
    protected val rootJob = SupervisorJob(parent = appScope.coroutineContext[Job])
    protected val rootScope =
        CoroutineScope(appScope.coroutineContext + rootJob + CoroutineName("KBus-Root"))
    private val eventDispatcherScope =
        CoroutineScope(
            rootScope.coroutineContext +
                SupervisorJob(parent = rootJob) +
                Dispatchers.Default +
                CoroutineName("KBus-EventDispatcher")
        )
    private val outboxScope =
        CoroutineScope(
            rootScope.coroutineContext +
                SupervisorJob(parent = rootJob) +
                Dispatchers.Default +
                CoroutineName("KBus-Outbox")
        )
    private val inboxScope =
        CoroutineScope(
            rootScope.coroutineContext +
                SupervisorJob(parent = rootJob) +
                Dispatchers.Default +
                CoroutineName("KBus-Inbox")
        )
    /**
     * One [BoundedContext] per identity, each dispatching integration events to its own handler
     * slice. Empty ⇒ a single [BoundedContextId.DEFAULT] context over the bus's shared locator.
     * Commands, queries and domain events resolve through [handlerLocator] regardless.
     */
    private val boundedContexts: List<BoundedContext> =
        contexts
            .ifEmpty { mapOf(BoundedContextId.DEFAULT to handlerLocator) }
            .map { (id, locator) ->
                BoundedContext(id, LocatorSubscriptions(locator), locator, { eventDispatcher })
            }

    private val inboxCoordinator = InboxCoordinator(inbox, boundedContexts, inboxScope)
    private val router = EventRouter(inboxCoordinator.destinations)
    private val directPublisher = DirectPublisher(router, eventDispatcherScope)
    private val outboxCoordinator = OutboxCoordinator(outbox, router, outboxScope)
    private val integrationEventPublisherFactory =
        IntegrationEventPublisherFactory(outboxCoordinator, directPublisher)
    private val contextFactory: MiddlewareInvocationContextFactory =
        MiddlewareInvocationContextFactory(integrationEventPublisherFactory)
    protected val eventDispatcher: EventDispatcher =
        EventDispatcher(
            handlerLocator::handlersFor,
            middlewares,
            eventDispatcherScope,
            contextFactory = contextFactory,
        )
    protected val commandExecutor =
        CommandExecutor(
            transactionManager,
            middlewares,
            contextFactory,
            DefaultCommandDependenciesFactory(eventDispatcher),
            CommandInvocationFactory(DefaultUnitOfWorkFactory(), integrationEventPublisherFactory),
        )
    protected val queryFetcher = QueryFetcher(middlewares, contextFactory)

    private enum class Lifecycle {
        NEW,
        STARTED,
        STOPPED,
    }

    private var lifecycle = Lifecycle.NEW

    /** True when this bus has background work that only [start] can set running. */
    private val requiresStart: Boolean
        get() =
            outboxCoordinator.isEnabled ||
                inboxCoordinator.isEnabled ||
                middlewares.any { it is LifecycleAwareMiddleware }

    /**
     * Starts this bus's background work: each [LifecycleAwareMiddleware]'s [onStart], then any
     * inbox pumps, then the outbox poller if one is configured — consumers before producers, so a
     * pre-existing backlog is already draining when the poller's first tick lands. A no-op on a bus
     * with none of these. Idempotent — calling this more than once has no further effect. Must be
     * called before [execute]/[fetch] on a bus with background work (see [requiresStart]); there is
     * no restart after [stop].
     */
    fun start() {
        if (lifecycle != Lifecycle.NEW) return
        lifecycle = Lifecycle.STARTED

        middlewares.forEach { middleware ->
            if (middleware is LifecycleAwareMiddleware) {
                val middlewareName = middleware::class.simpleName ?: "Unknown"
                val middlewareScope =
                    CoroutineScope(
                        rootScope.coroutineContext +
                            SupervisorJob(parent = rootJob) +
                            Dispatchers.Default +
                            CoroutineName("KBus-Middleware-$middlewareName")
                    )

                middleware.onStart(BusMiddlewareContext(middlewareScope))
            }
        }

        inboxCoordinator.startConsuming()
        outboxCoordinator.startPolling()
    }

    /**
     * Stops this bus: calls each [LifecycleAwareMiddleware]'s
     * [onStop][LifecycleAwareMiddleware.onStop], then gives in-flight [eventDispatcherScope] work
     * up to [gracePeriod] to finish before cancelling [rootJob] (and, with it, the outbox poller
     * and every scope derived from it) and suspending until that cancellation completes. A no-op if
     * [start] was never called. Terminal — a stopped bus cannot be restarted.
     *
     * The grace period is not the inbox's durability fix — an inboxed context already dispatches
     * inline on its own pump coroutine, so a cancelled pump simply leaves its envelope unacked for
     * the next start to retry, no draining required. What it buys completion for is the two
     * remaining detached, non-durable paths: a post-commit domain fire-and-forget handler, and
     * [DirectPublisher]'s launched fire-and-forget routing — both unawaited by the caller that
     * triggered them, and both lost outright if [rootJob] is cancelled mid-flight. The outbox and
     * inbox scopes are deliberately not drained here: they run pollers that should be cancelled
     * promptly, and their work is durable and resumes on restart.
     *
     * The join is a one-shot snapshot of [eventDispatcherScope]'s children at the moment [stop] is
     * called, not a fixed point: a handler that itself launches further detached work during the
     * grace period (e.g. a fire-and-forget handler publishing a further fire-and-forget event) is
     * not guaranteed to be joined, since it wasn't a child yet when the snapshot was taken. The
     * grace period bounds how long shutdown waits; it does not guarantee every detached hop
     * completes.
     */
    suspend fun stop(gracePeriod: Duration = DEFAULT_STOP_GRACE_PERIOD) {
        if (lifecycle != Lifecycle.STARTED) return
        lifecycle = Lifecycle.STOPPED

        middlewares.forEach { middleware ->
            if (middleware is LifecycleAwareMiddleware) middleware.onStop()
        }

        withTimeoutOrNull(gracePeriod) {
            eventDispatcherScope.coroutineContext[Job]!!.children.toList().joinAll()
        }

        rootJob.cancelAndJoin()
    }

    private fun checkStarted() =
        check(!requiresStart || lifecycle == Lifecycle.STARTED) {
            "This bus has background work (an outbox, an inbox, and/or lifecycle-aware " +
                "middleware) and must be started with start() before dispatching messages."
        }

    override suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
        command: TCommand
    ): TResult {
        checkStarted()
        val handlerCreator = { commandDependencies: CommandDependencies ->
            (handlerLocator.handlerFor(command, commandDependencies)
                ?: throw MissingHandlerException(command::class))
        }

        return commandExecutor.execute(command, handlerCreator)
    }

    override suspend fun <TQuery : Query<TResult>, TResult : KBusResult> fetch(
        query: TQuery
    ): TResult {
        checkStarted()
        val handlerCreator = {
            (handlerLocator.handlerFor(query) ?: throw MissingHandlerException(query::class))
        }

        return queryFetcher.fetch(query, handlerCreator)
    }

    fun <TEvent : IntegrationEvent> observe(eventClass: KClass<TEvent>): Flow<TEvent> =
        router.observerRegistry.observableFor(eventClass)

    inline fun <reified T : IntegrationEvent> observe(): Flow<T> = observe(T::class)
}

// TODO change KSP to use extension functions?
@Suppress("LongParameterList")
class MessageBus(
    handlerLocator: HandlerLocator = PersistingHandlerLocator(),
    transactionManager: TransactionManager? = EmptyTransactionManager(),
    middlewares: List<Middleware> = emptyList(),
    rootScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    outbox: OutboxConfig? = null,
    contexts: Map<BoundedContextId, HandlerLocator> = emptyMap(),
    inbox: InboxConfig? = null,
) :
    BaseMessageBus(
        handlerLocator,
        transactionManager,
        middlewares,
        rootScope,
        outbox,
        contexts,
        inbox,
    )
