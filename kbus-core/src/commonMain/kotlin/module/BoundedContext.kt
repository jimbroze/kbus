package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.core.module.inbox.ContextInbox
import com.jimbroze.kbus.core.registry.HandlerLocator
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator

/**
 * An authored bounded context: a user constructs one with an [id] and registers its domain-event
 * and integration-event handlers in the [register] lambda (command and query registration goes
 * through [handlerLocator] directly). A bus takes a list of these and derives the runtime object
 * that actually dispatches — a [BoundedContext] cannot own that itself, since dispatch needs the
 * bus's middleware, scope and dependency wiring, all constructed later.
 *
 * Registration is confined to that lambda so a constructed context has a fixed handler set.
 * Ownership of a command or query can then be settled, and conflicts reported, against the wiring
 * rather than against a later dispatch.
 *
 * Declaring an [inbox] gives this context durable, independently-acked delivery of the integration
 * events it subscribes to; a context that declares none dispatches synchronously.
 */
class BoundedContext(
    val id: BoundedContextId,
    internal val handlerLocator: HandlerLocator = PersistingHandlerLocator(),
    internal val inbox: ContextInbox? = null,
    register: ContextRegistration.() -> Unit = {},
) {
    init {
        ContextRegistration(handlerLocator).register()
    }
}
