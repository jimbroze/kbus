package com.jimbroze.kbus.core.middleware

import kotlinx.coroutines.CoroutineScope

interface MiddlewareContext {
    val scope: CoroutineScope
}

interface LifecycleAwareMiddleware : Middleware {
    fun onStart(context: MiddlewareContext)

    fun onStop() = Unit
}

internal data class BusMiddlewareContext(override val scope: CoroutineScope) : MiddlewareContext
