package com.jimbroze.kbus.core.middleware.infrastructure

import kotlinx.coroutines.CoroutineScope

interface MiddlewareContext {
    val scope: CoroutineScope
}

interface LifecycleAwareMiddleware : Middleware {
    fun onStart(context: MiddlewareContext)

    /**
     * Runs inside the bus's stop grace period, before the scope handed to [onStart] is cancelled —
     * so an implementation that has non-durable work in flight can await it here rather than have
     * it cancelled from under it. Suspending past the grace period is not an option: the bus
     * cancels this call and proceeds, so shutdown stays bounded.
     */
    suspend fun onStop() = Unit
}

internal data class BusMiddlewareContext(override val scope: CoroutineScope) : MiddlewareContext
