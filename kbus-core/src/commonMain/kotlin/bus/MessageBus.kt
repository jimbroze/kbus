package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.contracts.bus.BusAccess
import com.jimbroze.kbus.contracts.common.MissingHandlerException
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.query.Query
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.messages.command.CommandExecutor
import com.jimbroze.kbus.core.messages.command.DefaultCommandDependenciesFactory
import com.jimbroze.kbus.core.messages.event.BusIntegrationEventPublisher
import com.jimbroze.kbus.core.messages.event.EventDispatcher
import com.jimbroze.kbus.core.messages.query.QueryFetcher
import com.jimbroze.kbus.core.middleware.BusMiddlewareContext
import com.jimbroze.kbus.core.middleware.LifecycleAwareMiddleware
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareInvocationContext
import com.jimbroze.kbus.core.registry.HandlerLocator
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.uow.DefaultUnitOfWorkFactory
import com.jimbroze.kbus.core.uow.EmptyTransactionManager
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.core.uow.OutboxPoller
import com.jimbroze.kbus.core.uow.TransactionOutbox
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

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
    private val busAccess =
        object : BusAccess {
            override suspend fun <TEvent : IntegrationEvent> dispatch(event: TEvent) =
                this@BaseMessageBus.dispatchIntegration(event)
        }
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
    protected val eventDispatcher =
        EventDispatcher(
            handlerLocator::handlersFor,
            middlewares,
            eventDispatcherScope,
            invocationContextProvider = { invocationContext },
        )
    private val integrationEventPublisher =
        BusIntegrationEventPublisher(handlerLocator, eventDispatcher)
    private val invocationContext: MiddlewareInvocationContext =
        object : MiddlewareInvocationContext {
            override val integrationEventPublisher = this@BaseMessageBus.integrationEventPublisher
        }
    private val outboxScope =
        CoroutineScope(
            rootScope.coroutineContext +
                SupervisorJob(parent = rootJob) +
                Dispatchers.Default +
                CoroutineName("KBus-Outbox")
        )
    private val outboxFactory: (() -> TransactionOutbox)? =
        outbox?.let { config ->
            {
                TransactionOutbox(
                    config.store,
                    integrationEventPublisher,
                    outboxScope,
                    config.drainAfterCommit,
                )
            }
        }
    protected val commandExecutor =
        CommandExecutor(
            transactionManager,
            middlewares,
            busAccess,
            DefaultCommandDependenciesFactory(eventDispatcher),
            DefaultUnitOfWorkFactory(outboxFactory),
            invocationContextProvider = { invocationContext },
        )
    protected val queryFetcher =
        QueryFetcher(middlewares, invocationContextProvider = { invocationContext })

    init {
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

        if (outbox != null) {
            outboxScope.launch {
                OutboxPoller(
                        outbox.store,
                        integrationEventPublisher,
                        outbox.batchSize,
                        outbox.pollInterval,
                    )
                    .run()
            }
        }
    }

    override suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
        command: TCommand
    ): TResult {
        val handlerCreator = { commandDependencies: CommandDependencies ->
            (handlerLocator.handlerFor(command, commandDependencies)
                ?: throw MissingHandlerException(command::class))
        }

        return commandExecutor.execute(command, handlerCreator)
    }

    override suspend fun <TQuery : Query<TResult>, TResult : KBusResult> fetch(
        query: TQuery
    ): TResult {
        val handlerCreator = {
            (handlerLocator.handlerFor(query) ?: throw MissingHandlerException(query::class))
        }

        return queryFetcher.fetch(query, handlerCreator)
    }

    private suspend fun <TEvent : IntegrationEvent> dispatchIntegration(event: TEvent) {
        this.integrationEventPublisher.publish(listOf(event))
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
