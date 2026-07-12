package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.core.middleware.LifecycleAwareMiddleware
import com.jimbroze.kbus.core.middleware.Middleware
import com.jimbroze.kbus.core.middleware.MiddlewareContext
import com.jimbroze.kbus.core.middleware.MiddlewareHandler
import com.jimbroze.kbus.core.middleware.MiddlewareInvocationContext

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
