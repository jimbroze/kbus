package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.core.module.inbox.ContextInbox
import com.jimbroze.kbus.core.registry.HandlerLocator
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator

/**
 * One bounded context's handlers, declared by the user and passed to a bus. Event handlers are
 * registered in the [register] lambda; commands and queries go through [handlerLocator] directly.
 *
 * Registration is confined to that lambda so a constructed context has a fixed handler set, letting
 * a bus settle which context owns each command and report conflicts while wiring up rather than on
 * a later dispatch.
 *
 * Declaring an [inbox] gives this context durable, independently acknowledged delivery of the
 * integration events it subscribes to; a context that declares none dispatches synchronously.
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
