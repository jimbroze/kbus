package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.core.messages.command.CommandInvocationFactory
import com.jimbroze.kbus.core.messages.event.publish.DirectPublisher
import com.jimbroze.kbus.core.messages.event.publish.IntegrationEventPublisherFactory
import com.jimbroze.kbus.core.messages.event.routing.EventRouter
import com.jimbroze.kbus.core.middleware.LifecycleAwareMiddleware
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareContext
import com.jimbroze.kbus.core.middleware.MiddlewareHandler
import com.jimbroze.kbus.core.middleware.MiddlewareInvocationContext
import com.jimbroze.kbus.core.middleware.MiddlewareInvocationContextFactory
import com.jimbroze.kbus.core.uow.DefaultUnitOfWorkFactory
import com.jimbroze.kbus.core.uow.OutboxConfig
import com.jimbroze.kbus.core.uow.OutboxCoordinator
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

object EmptyMiddlewareInvocationContext : MiddlewareInvocationContext {
    override val integrationEventPublisher = EmptyIntegrationEventPublisher
}

/** Bundles the bus-owned factories over the same direct publisher, for test wiring. */
class TestPublisherFactories(
    directPublisher: DirectPublisher =
        DirectPublisher(EventRouter(emptyList()), CoroutineScope(Job())),
    outboxConfig: OutboxConfig? = null,
    router: EventRouter = EventRouter(emptyList()),
    outboxScope: CoroutineScope = CoroutineScope(Job()),
) {
    private val publisherFactory =
        IntegrationEventPublisherFactory(
            OutboxCoordinator(outboxConfig, router, outboxScope),
            directPublisher,
        )
    val contextFactory = MiddlewareInvocationContextFactory(publisherFactory)
    val invocationFactory = CommandInvocationFactory(DefaultUnitOfWorkFactory(), publisherFactory)
}

/**
 * An [IntegrationEventPublisherFactory] with no outbox configured, always yielding
 * [directPublisher].
 */
fun noOutboxPublisherFactory(
    directPublisher: DirectPublisher =
        DirectPublisher(EventRouter(emptyList()), CoroutineScope(Job()))
): IntegrationEventPublisherFactory =
    IntegrationEventPublisherFactory(
        OutboxCoordinator(null, EventRouter(emptyList()), CoroutineScope(Job())),
        directPublisher,
    )

fun emptyContextFactory(): MiddlewareInvocationContextFactory =
    MiddlewareInvocationContextFactory(noOutboxPublisherFactory())

class CapturingLifecycleMiddleware(private val name: String = "CapturingLifecycle") :
    LifecycleAwareMiddleware {
    var startContext: MiddlewareContext? = null
        private set

    var stopped = false
        private set

    override fun onStart(context: MiddlewareContext) {
        startContext = context
    }

    override suspend fun onStop() {
        stopped = true
    }

    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        context: MiddlewareInvocationContext,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult = nextMiddleware(message)

    override fun toString(): String = name
}

class PassthroughMiddleware : Middleware {
    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        context: MiddlewareInvocationContext,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult = nextMiddleware(message)
}

class CapturingContextMiddleware : Middleware {
    private val capturedContexts = mutableListOf<Pair<KClass<*>, MiddlewareInvocationContext>>()

    val capturedContext: MiddlewareInvocationContext?
        get() = capturedContexts.lastOrNull()?.second

    fun contextFor(messageClass: KClass<*>): MiddlewareInvocationContext? =
        capturedContexts.firstOrNull { it.first == messageClass }?.second

    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        context: MiddlewareInvocationContext,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        capturedContexts.add(message::class to context)
        return nextMiddleware(message)
    }
}
