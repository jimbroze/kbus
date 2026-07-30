package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.contracts.common.AmbiguousHandlerException
import com.jimbroze.kbus.contracts.common.Message
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
import com.jimbroze.kbus.core.messages.event.dispatch.DomainEventDispatcher
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
import com.jimbroze.kbus.core.module.ContextRuntime
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

/**
 * @param appScope the scope every bus-owned scope derives from. Its dispatcher is inherited, not
 *   overridden, so a supplied dispatcher governs dispatch, outbox, inbox and middleware coroutines
 *   too — a scope carrying none leaves them on [Dispatchers.Default].
 */
@Suppress("LongParameterList")
abstract class BaseMessageBus(
    protected val handlerLocator: HandlerLocator,
    transactionManager: TransactionManager?,
    protected val middlewares: List<Middleware>,
    appScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    outbox: OutboxConfig? = null,
    contexts: List<BoundedContext> = emptyList(),
    inbox: InboxConfig? = null,
) : IMessageBus {
    protected val rootJob = SupervisorJob(parent = appScope.coroutineContext[Job])
    protected val rootScope =
        CoroutineScope(appScope.coroutineContext + rootJob + CoroutineName("KBus-Root"))
    private val eventDispatcherScope =
        CoroutineScope(
            rootScope.coroutineContext +
                SupervisorJob(parent = rootJob) +
                CoroutineName("KBus-EventDispatcher")
        )
    private val outboxScope =
        CoroutineScope(
            rootScope.coroutineContext +
                SupervisorJob(parent = rootJob) +
                CoroutineName("KBus-Outbox")
        )
    private val inboxScope =
        CoroutineScope(
            rootScope.coroutineContext +
                SupervisorJob(parent = rootJob) +
                CoroutineName("KBus-Inbox")
        )
    /**
     * One runtime per authored [BoundedContext], each dispatching integration events — and, as a
     * [com.jimbroze.kbus.core.messages.event.dispatch.DomainEventDispatcher], domain events — to
     * its own handler slice. An empty [contexts] gives a single [BoundedContextId.DEFAULT] context
     * over the bus's shared [handlerLocator], so non-modular apps are unaffected. Commands, queries
     * and domain events all resolve per-context: a command's owning context (see [ownerRuntimeFor])
     * determines both its handler and which context's domain handlers its domain events reach.
     *
     * Each runtime's [EventDispatcher] is created lazily. Its [contextFactory] transitively depends
     * on the router these runtimes feed into, so building one eagerly here would be a circular
     * initializer.
     */
    private val contextRuntimes: List<ContextRuntime> =
        contexts
            .ifEmpty { listOf(BoundedContext(BoundedContextId.DEFAULT, handlerLocator)) }
            .map { context ->
                ContextRuntime(
                    context,
                    eventDispatcher =
                        lazy {
                            EventDispatcher(
                                context.handlerLocator::handlersFor,
                                middlewares,
                                eventDispatcherScope,
                                contextFactory = contextFactory,
                            )
                        },
                )
            }

    init {
        val duplicates =
            contextRuntimes.groupingBy { it.context.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) {
            "Duplicate BoundedContextId(s): ${duplicates.map { it.value }}. " +
                "Each bounded context must have a unique id."
        }

        contextRuntimes.forEach { it.context.seal() }
    }

    private val inboxCoordinator = InboxCoordinator(inbox, contextRuntimes, inboxScope)
    private val router = EventRouter(inboxCoordinator.destinations)
    private val directPublisher = DirectPublisher(router, eventDispatcherScope)
    private val outboxCoordinator = OutboxCoordinator(outbox, router, outboxScope)
    private val integrationEventPublisherFactory =
        IntegrationEventPublisherFactory(outboxCoordinator, directPublisher)
    private val contextFactory: MiddlewareInvocationContextFactory =
        MiddlewareInvocationContextFactory(integrationEventPublisherFactory)

    protected val commandExecutor =
        CommandExecutor(
            transactionManager,
            middlewares,
            contextFactory,
            DefaultCommandDependenciesFactory(),
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
                            CoroutineName("KBus-Middleware-$middlewareName")
                    )

                middleware.onStart(BusMiddlewareContext(middlewareScope))
            }
        }

        inboxCoordinator.startConsuming()
        outboxCoordinator.startPolling()
    }

    /**
     * Stops this bus: within one [gracePeriod] budget, calls each [LifecycleAwareMiddleware]'s
     * [onStop][LifecycleAwareMiddleware.onStop] and then lets in-flight [eventDispatcherScope] work
     * finish; then cancels [rootJob] (and, with it, the outbox poller and every scope derived from
     * it) and waits, for at most another [gracePeriod], for that cancellation to complete. A no-op
     * if [start] was never called. Terminal — a stopped bus cannot be restarted.
     *
     * The `onStop` calls and the dispatch drain share one budget, sequentially: a middleware that
     * suspends for the whole grace period starves the ones after it and the drain. That is
     * deliberate — the alternative is a per-participant budget, which makes worst-case shutdown
     * scale with the number of middlewares.
     *
     * The wait on cancellation is bounded because cancellation is cooperative and there is no hard
     * kill: a coroutine that never reaches a suspension point, or that suspends inside
     * `NonCancellable`, would otherwise block shutdown forever. Bounding it means such a coroutine
     * leaks — orphaned but still running — rather than hanging the application's exit. The bus is
     * already [Lifecycle.STOPPED] by then and dispatches nothing further.
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
     * The drain waits for quiescence, not for one snapshot of [eventDispatcherScope]'s children:
     * work launched *during* the grace period (a fire-and-forget handler publishing a further
     * fire-and-forget event) becomes a child only after a snapshot would have been taken, so the
     * children are re-read until none remain. Only that scope's subtree is covered — a handler that
     * launches onto a scope the bus doesn't own is still cancelled mid-flight — and the grace
     * period bounds the whole loop, so work that spawns replacements faster than they complete is
     * cut off rather than spun on forever.
     */
    suspend fun stop(gracePeriod: Duration = DEFAULT_STOP_GRACE_PERIOD) {
        if (lifecycle != Lifecycle.STARTED) return
        lifecycle = Lifecycle.STOPPED

        withTimeoutOrNull(gracePeriod) {
            middlewares.forEach { middleware ->
                if (middleware is LifecycleAwareMiddleware) middleware.onStop()
            }

            val dispatchJob = eventDispatcherScope.coroutineContext[Job]!!
            while (true) {
                val inFlight = dispatchJob.children.toList()
                if (inFlight.isEmpty()) break
                inFlight.joinAll()
            }
        }

        rootJob.cancel()
        withTimeoutOrNull(gracePeriod) { rootJob.join() }
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
        val owner = ownerRuntimeFor(command::class) { it.hasHandlerFor(command) }
        val handlerCreator = { commandDependencies: CommandDependencies ->
            (owner.context.handlerLocator.handlerFor(command, commandDependencies)
                ?: throw MissingHandlerException(command::class))
        }

        return commandExecutor.execute(command, owner, handlerCreator)
    }

    override suspend fun <TQuery : Query<TResult>, TResult : KBusResult> fetch(
        query: TQuery
    ): TResult {
        checkStarted()
        val owner = ownerRuntimeFor(query::class) { it.hasHandlerFor(query) }
        val handlerCreator = {
            (owner.context.handlerLocator.handlerFor(query)
                ?: throw MissingHandlerException(query::class))
        }

        return queryFetcher.fetch(query, handlerCreator)
    }

    /**
     * Finds the single [contextRuntimes] entry [hasHandler] answers true for — a command or query
     * is single-owner by contract, so zero owners is [MissingHandlerException] and two or more is
     * [AmbiguousHandlerException] rather than resolved by list order. Returns the owning runtime
     * rather than its id, so a command's domain dispatcher is the resolved object itself and can
     * never be re-derived wrongly.
     */
    private fun ownerRuntimeFor(
        messageClass: KClass<out Message>,
        hasHandler: (HandlerLocator) -> Boolean,
    ): ContextRuntime {
        val owners = contextRuntimes.filter { hasHandler(it.context.handlerLocator) }
        return when (owners.size) {
            0 -> throw MissingHandlerException(messageClass)
            1 -> owners.single()
            else ->
                throw AmbiguousHandlerException(messageClass, owners.map { it.context.id.value })
        }
    }

    /**
     * The domain-event dispatcher of the context [contextId] names. Generated command functions
     * know their handler's owning context statically and so skip [ownerRuntimeFor]'s search; this
     * is the one place that turns such an id back into a dispatcher, and it throws rather than
     * silently dispatching to no one when the id names no context on this bus.
     */
    protected fun domainEventDispatcherFor(contextId: BoundedContextId): DomainEventDispatcher =
        contextRuntimes.firstOrNull { it.context.id == contextId }
            ?: throw IllegalArgumentException(
                "No bounded context with id '${contextId.value}' on this bus."
            )

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
    appScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    outbox: OutboxConfig? = null,
    contexts: List<BoundedContext> = emptyList(),
    inbox: InboxConfig? = null,
) :
    BaseMessageBus(
        handlerLocator,
        transactionManager,
        middlewares,
        appScope,
        outbox,
        contexts,
        inbox,
    )
