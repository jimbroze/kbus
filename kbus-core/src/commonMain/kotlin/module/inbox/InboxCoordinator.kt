package com.jimbroze.kbus.core.module.inbox

import com.jimbroze.kbus.contracts.inbox.InboxStore
import com.jimbroze.kbus.contracts.messages.event.EventDestination
import com.jimbroze.kbus.core.module.BoundedContext
import com.jimbroze.kbus.core.module.BoundedContextId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Opt-in, per-[BoundedContextId] durable inbox configuration. Each context in [stores] gets its own
 * [InboxStore] instance — structural isolation, so one context's pump cannot see another context's
 * rows. A context absent from [stores] keeps today's synchronous dispatch.
 */
class InboxConfig(
    val stores: Map<BoundedContextId, InboxStore>,
    val pollInterval: Duration = 30.seconds,
    val batchSize: Int = 100,
    val opportunisticDispatch: Boolean = true,
) {
    init {
        require(stores.isNotEmpty()) {
            "InboxConfig with no stores does nothing; pass inbox = null instead."
        }
    }
}

/**
 * Wraps each [contexts] entry that has a configured store in [config] with an [EventInbox], and
 * leaves the rest untouched. Mirrors [com.jimbroze.kbus.core.uow.OutboxCoordinator]'s shape:
 * config-or-null and a scope in, derived members as `val`s, an idempotent start, and no `stop` —
 * cancellation is the bus's root job.
 */
class InboxCoordinator(
    private val config: InboxConfig?,
    contexts: List<BoundedContext>,
    private val inboxScope: CoroutineScope,
) {
    val destinations: List<EventDestination> =
        contexts.map { context ->
            config?.stores?.get(context.id)?.let { store ->
                EventInbox(
                    context,
                    store,
                    inboxScope,
                    config.batchSize,
                    config.pollInterval,
                    config.opportunisticDispatch,
                )
            } ?: context
        }

    private val inboxes = destinations.filterIsInstance<EventInbox>()

    init {
        val known = contexts.map { it.id }.toSet()
        val unknown = config?.stores?.keys.orEmpty() - known
        require(unknown.isEmpty()) {
            "InboxConfig has stores for bounded contexts this bus has no context for: " +
                "${unknown.map { it.value }}. Known contexts: ${known.map { it.value }}."
        }
    }

    val isEnabled: Boolean
        get() = inboxes.isNotEmpty()

    private var pumpJobs: List<Job>? = null

    /** Idempotent; a no-op when no inbox is configured. One job per inbox. */
    fun startConsuming() {
        if (pumpJobs != null || inboxes.isEmpty()) return
        pumpJobs = inboxes.map { inbox -> inboxScope.launch { inbox.pump() } }
    }
}
