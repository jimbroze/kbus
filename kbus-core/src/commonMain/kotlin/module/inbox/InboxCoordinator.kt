package com.jimbroze.kbus.core.module.inbox

import com.jimbroze.kbus.contracts.inbox.InboxStore
import com.jimbroze.kbus.contracts.messages.event.ErrorStrategy
import com.jimbroze.kbus.contracts.messages.event.EventDestination
import com.jimbroze.kbus.core.module.BoundedContext
import com.jimbroze.kbus.core.module.BoundedContextId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Consumer-side ack policy for an inboxed context — independent of every other context's, and
 * independent of the event's own [ErrorStrategy] declaration. Producers own event data; consumers
 * own consumption policy.
 */
sealed interface InboxAckPolicy {
    /** Ack exactly as the event's own [ErrorStrategy] dictates. */
    data object HonourEventStrategy : InboxAckPolicy

    /**
     * Refuses a producer's "don't care": an [ErrorStrategy.FireAndForget] event is dispatched as if
     * it were [ErrorStrategy.ContinueAndAggregate], so a handler failure leaves the envelope
     * pending and the next pump tick retries it. [ErrorStrategy.FailFast] and
     * [ErrorStrategy.ContinueAndAggregate] events are unaffected — they already retry on failure.
     */
    data object RequireHandlerSuccess : InboxAckPolicy
}

/** `null` for [InboxAckPolicy.HonourEventStrategy]: no override, dispatch exactly as declared. */
internal val InboxAckPolicy.errorStrategyOverride: ((ErrorStrategy) -> ErrorStrategy)?
    get() =
        when (this) {
            InboxAckPolicy.HonourEventStrategy -> null
            InboxAckPolicy.RequireHandlerSuccess -> { strategy ->
                    if (strategy == ErrorStrategy.FireAndForget) ErrorStrategy.ContinueAndAggregate
                    else strategy
                }
        }

/**
 * Opt-in, per-[BoundedContextId] durable inbox configuration. Each context in [stores] gets its own
 * [InboxStore] instance — structural isolation, so one context's pump cannot see another context's
 * rows. A context absent from [stores] keeps today's synchronous dispatch.
 *
 * [ackPolicy] is deliberately required rather than defaulted: either default would silently pick a
 * side of a durability trade-off the consumer owns — [InboxAckPolicy.HonourEventStrategy] lets a
 * producer's [ErrorStrategy.FireAndForget] ack a failed handler, and
 * [InboxAckPolicy.RequireHandlerSuccess] retries it indefinitely (there is no attempt cap or
 * dead-letter path yet). It applies only to the contexts that have a configured store — it is
 * meaningless without a durable ack to make stronger.
 */
class InboxConfig(
    val stores: Map<BoundedContextId, InboxStore>,
    val ackPolicy: InboxAckPolicy,
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
                    context.withAckStrategy(config.ackPolicy.errorStrategyOverride),
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
