package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.core.middleware.LifecycleAwareMiddleware
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareContext
import com.jimbroze.kbus.core.middleware.MiddlewareHandler
import com.jimbroze.kbus.core.middleware.MiddlewareInvocationContext
import kotlin.reflect.KClass

class CapturingLifecycleMiddleware(private val name: String = "CapturingLifecycle") :
    LifecycleAwareMiddleware {
    var startContext: MiddlewareContext? = null
        private set

    var stopped = false
        private set

    override fun onStart(context: MiddlewareContext) {
        startContext = context
    }

    override fun onStop() {
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
