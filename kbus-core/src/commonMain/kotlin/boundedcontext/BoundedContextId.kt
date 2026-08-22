package com.jimbroze.kbus.core.boundedcontext

import kotlin.jvm.JvmInline

/**
 * The identity of one bounded context.
 *
 * Lives in `kbus-core` rather than the handler-authoring API: an `EventDestination` is not
 * necessarily a module (an external transport is a destination too), and nothing a handler author
 * writes needs the type. It is the join key between a producing module's KSP run and the contexts a
 * generated bus builds.
 */
@JvmInline
value class BoundedContextId(val value: String) {
    companion object {
        /**
         * The context every handler belongs to until a bounded context identity assigns it
         * elsewhere — also the single context of a bus configured with none.
         */
        val DEFAULT = BoundedContextId("default")
    }
}
