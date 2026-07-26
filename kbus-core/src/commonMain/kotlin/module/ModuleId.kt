package com.jimbroze.kbus.core.module

import kotlin.jvm.JvmInline

/**
 * The identity of one bounded context.
 *
 * Lives in `kbus-core` rather than contracts: an `EventDestination` is not necessarily a module (an
 * external transport is a destination too), and nothing in the contracts module needs the type. It
 * is the join key between a producing module's KSP run and the contexts a generated bus builds.
 */
@JvmInline
value class ModuleId(val value: String) {
    companion object {
        /**
         * The context every handler belongs to until a module identity assigns it elsewhere — also
         * the single context of a bus configured with none.
         */
        val DEFAULT = ModuleId("default")
    }
}
