package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.core.messages.event.dispatch.EventDispatcher

// SKETCH ONLY — Option A of the "who builds ContextRuntime" question. Not wired up, not tested.

/**
 * Turns a declared [BoundedContext] into the context a bus runs it as.
 *
 * The factory is also the registry: a bus reads back every runtime built through it, so declaring a
 * context and registering it on the bus are one act. A context that exists but was never registered
 * — today's silent failure, since nothing checks that a `Contexts` class put every context it built
 * into its own list — cannot be written.
 */
class ContextRuntimeFactory
internal constructor(private val dispatcherFor: (BoundedContext) -> Lazy<EventDispatcher>) {
    private val runtimes = mutableListOf<ContextRuntime>()

    internal val built: List<ContextRuntime>
        get() = runtimes

    fun runtimeFor(context: BoundedContext): OwningContext =
        ContextRuntime(context, dispatcherFor(context)).also { runtimes += it }
}

/** The contexts of a bus whose contexts are not known statically — a hand-written one. */
class DefaultContexts internal constructor(@Suppress("unused") internal val all: List<OwningContext>)
