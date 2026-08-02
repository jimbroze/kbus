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
import com.jimbroze.kbus.core.module.OwningContext
import com.jimbroze.kbus.core.module.inbox.InboxCoordinator
import com.jimbroze.kbus.core.module.inbox.InboxTuning
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
    contexts: List<BoundedContext>,
    transactionManager: TransactionManager,
    protected val middlewares: List<Middleware>,
    appScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    outbox: OutboxConfig? = null,
    inboxTuning: InboxTuning? = null,
) : IMessageBus {
    /**
     * A bus over a single implicit default context, for apps that draw no context boundaries. The
     * locator and a set of contexts are alternatives, not a pair, so neither can be silently
     * ignored in favour of the other.
     */
    constructor(
        handlerLocator: HandlerLocator,
        transactionManager: TransactionManager,
        middlewares: List<Middleware>,
        appScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
        outbox: OutboxConfig? = null,
        inboxTuning: InboxTuning? = null,
    ) : this(
        listOf(BoundedContext(BoundedContextId.DEFAULT, handlerLocator)),
        transactionManager,
        middlewares,
        appScope,
        outbox,
        inboxTuning,
    )

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
     * One runtime per declared [BoundedContext].
     *
     * Dispatchers are built lazily to break a cycle: a dispatcher depends on the router, which
     * depends on these runtimes.
     */
    private val contextRuntimes: List<ContextRuntime> =
        contexts.map { context ->
            ContextRuntime(
                context,
                eventDispatcher =
                    lazy {
                        EventDispatcher(
                            context.handlerLocator::domainHandlersFor,
                            middlewares,
                            eventDispatcherScope,
                            contextFactory = contextFactory,
                        )
                    },
            )
        }

    init {
        require(contextRuntimes.isNotEmpty()) {
            "A bus needs at least one bounded context to resolve handlers from."
        }
        val duplicates =
            contextRuntimes.groupingBy { it.context.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) {
            "Duplicate BoundedContextId(s): ${duplicates.map { it.value }}. " +
                "Each bounded context must have a unique id."
        }
    }

    private val commandOwners = indexOwners { it.handledCommandTypes() }
    private val queryOwners = indexOwners { it.handledQueryTypes() }

    private val inboxCoordinator = InboxCoordinator(inboxTuning, contextRuntimes, inboxScope)
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
     * Starts this bus's background work. Idempotent, and a no-op on a bus that has none. Must be
     * called before [execute]/[fetch] on a bus that does; there is no restart after [stop].
     *
     * Consumers start before producers, so a pre-existing backlog is already draining when the
     * outbox poller's first tick lands.
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
     * Stops this bus, letting in-flight event dispatch finish first. A no-op if [start] was never
     * called. Terminal — a stopped bus cannot be restarted.
     *
     * [gracePeriod] is one shared budget, spent sequentially on each [LifecycleAwareMiddleware]'s
     * [onStop][LifecycleAwareMiddleware.onStop] and then on draining dispatch, so a middleware that
     * suspends for all of it starves those after it. A second [gracePeriod] bounds the wait on
     * cancellation; a coroutine that outlives it is orphaned but left running, since cancellation
     * is cooperative and hanging shutdown forever is the worse failure.
     *
     * Only dispatch is drained. Outbox and inbox work is durable and resumes on the next start, so
     * their pollers are cancelled promptly instead. Handlers that launch onto a scope the bus does
     * not own are cancelled mid-flight.
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
        val owner = commandOwners[command::class] ?: throw MissingHandlerException(command::class)
        val handlerCreator = { commandDependencies: CommandDependencies ->
            (owner.handlerFor(command, commandDependencies)
                ?: throw MissingHandlerException(command::class))
        }

        return commandExecutor.execute(command, owner, handlerCreator)
    }

    override suspend fun <TQuery : Query<TResult>, TResult : KBusResult> fetch(
        query: TQuery
    ): TResult {
        checkStarted()
        val owner = queryOwners[query::class] ?: throw MissingHandlerException(query::class)
        val handlerCreator = {
            (owner.context.handlerLocator.handlerFor(query)
                ?: throw MissingHandlerException(query::class))
        }

        return queryFetcher.fetch(query, handlerCreator)
    }

    /**
     * Which context owns each message, read once from the contexts' handlers. Handlers registered
     * after the bus is built are not honoured, which is safe only because registration closes at
     * construction — and is what lets single-owner conflicts be reported against the wiring here
     * rather than against whichever dispatch happens to hit one first.
     */
    private fun indexOwners(
        handledTypes: (HandlerLocator) -> Set<KClass<out Message>>
    ): Map<KClass<out Message>, ContextRuntime> {
        val owners = mutableMapOf<KClass<out Message>, ContextRuntime>()
        for (runtime in contextRuntimes) {
            for (messageClass in handledTypes(runtime.context.handlerLocator)) {
                val existingOwner = owners.put(messageClass, runtime)
                if (existingOwner != null) {
                    throw AmbiguousHandlerException(
                        messageClass,
                        listOf(existingOwner.context.id.value, runtime.context.id.value),
                    )
                }
            }
        }
        return owners
    }

    /**
     * The context [contextId] names, for callers that already know a command's owning context
     * statically. Throws if no such context is on this bus, rather than silently executing against
     * no one.
     */
    protected fun owningContextFor(contextId: BoundedContextId): OwningContext =
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
class MessageBus : BaseMessageBus {
    constructor(
        handlerLocator: HandlerLocator = PersistingHandlerLocator(),
        transactionManager: TransactionManager = EmptyTransactionManager(),
        middlewares: List<Middleware> = emptyList(),
        appScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
        outbox: OutboxConfig? = null,
        inboxTuning: InboxTuning? = null,
    ) : super(handlerLocator, transactionManager, middlewares, appScope, outbox, inboxTuning)

    constructor(
        contexts: List<BoundedContext>,
        transactionManager: TransactionManager = EmptyTransactionManager(),
        middlewares: List<Middleware> = emptyList(),
        appScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
        outbox: OutboxConfig? = null,
        inboxTuning: InboxTuning? = null,
    ) : super(contexts, transactionManager, middlewares, appScope, outbox, inboxTuning)
}
