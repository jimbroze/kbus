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
import com.jimbroze.kbus.core.messages.event.BusIntegrationEventPublisher
import com.jimbroze.kbus.core.messages.event.EventDispatcher
import com.jimbroze.kbus.core.messages.event.IntegrationEventPublisherFactory
import com.jimbroze.kbus.core.messages.query.QueryFetcher
import com.jimbroze.kbus.core.middleware.BusMiddlewareContext
import com.jimbroze.kbus.core.middleware.LifecycleAwareMiddleware
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareInvocationContextFactory
import com.jimbroze.kbus.core.registry.HandlerLocator
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.uow.DefaultUnitOfWorkFactory
import com.jimbroze.kbus.core.uow.EmptyTransactionManager
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.core.uow.OutboxCoordinator
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow

interface IMessageBus {
    suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
        command: TCommand
    ): TResult

    suspend fun <TQuery : Query<TResult>, TResult : KBusResult> fetch(query: TQuery): TResult
}

abstract class BaseMessageBus(
    protected val handlerLocator: HandlerLocator,
    transactionManager: TransactionManager?,
    protected val middlewares: List<Middleware>,
    appScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    outbox: OutboxConfig? = null,
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
    private val baseIntegrationEventPublisher: BusIntegrationEventPublisher =
        BusIntegrationEventPublisher(handlerLocator) { eventDispatcher }
    private val outboxCoordinator =
        OutboxCoordinator(outbox, baseIntegrationEventPublisher, outboxScope)
    private val integrationEventPublisherFactory =
        IntegrationEventPublisherFactory(outboxCoordinator, baseIntegrationEventPublisher)
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
        get() = outboxCoordinator.isEnabled || middlewares.any { it is LifecycleAwareMiddleware }

    /**
     * Starts this bus's background work: each [LifecycleAwareMiddleware]'s [onStart], then the
     * outbox poller if one is configured. A no-op on a bus with neither. Idempotent — calling this
     * more than once has no further effect. Must be called before [execute]/[fetch] on a bus with
     * background work (see [requiresStart]); there is no restart after [stop].
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

        outboxCoordinator.startPolling()
    }

    /**
     * Stops this bus: calls each [LifecycleAwareMiddleware]'s
     * [onStop][LifecycleAwareMiddleware.onStop], then cancels [rootJob] (and, with it, the outbox
     * poller and every scope derived from it) and suspends until that cancellation completes. A
     * no-op if [start] was never called. Terminal — a stopped bus cannot be restarted.
     */
    suspend fun stop() {
        if (lifecycle != Lifecycle.STARTED) return
        lifecycle = Lifecycle.STOPPED

        middlewares.forEach { middleware ->
            if (middleware is LifecycleAwareMiddleware) middleware.onStop()
        }

        rootJob.cancelAndJoin()
    }

    private fun checkStarted() =
        check(!requiresStart || lifecycle == Lifecycle.STARTED) {
            "This bus has background work (an outbox and/or lifecycle-aware middleware) and must " +
                "be started with start() before dispatching messages."
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
        eventDispatcher.observerRegistry.observableFor(eventClass)

    inline fun <reified T : IntegrationEvent> observe(): Flow<T> = observe(T::class)
}

// TODO change KSP to use extension functions?
class MessageBus(
    handlerLocator: HandlerLocator = PersistingHandlerLocator(),
    transactionManager: TransactionManager? = EmptyTransactionManager(),
    middlewares: List<Middleware> = emptyList(),
    rootScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    outbox: OutboxConfig? = null,
) : BaseMessageBus(handlerLocator, transactionManager, middlewares, rootScope, outbox)
