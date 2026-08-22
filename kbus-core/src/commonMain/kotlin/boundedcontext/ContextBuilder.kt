package com.jimbroze.kbus.core.boundedcontext

import com.jimbroze.kbus.application.messages.command.NestedCommandExecutor
import com.jimbroze.kbus.core.messages.event.dispatch.EventDispatcher

/**
 * Turns a declared [BoundedContext] into the context a bus runs, and is the bus's record of which
 * contexts exist. Registering is the only way to obtain an [OwningContext], so a context a caller
 * holds is always one the bus knows about.
 */
class ContextBuilder
internal constructor(private val eventDispatcherFor: (BoundedContext) -> Lazy<EventDispatcher>) {
    internal val registeredContexts = mutableListOf<ContextRuntime>()

    fun register(context: BoundedContext): CommandOwningContext<NestedCommandExecutor> =
        ContextRuntime(context, eventDispatcherFor(context)).also { registeredContexts += it }
}
