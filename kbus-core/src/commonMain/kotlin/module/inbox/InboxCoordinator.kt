package com.jimbroze.kbus.core.module.inbox

import com.jimbroze.kbus.contracts.inbox.InboxStore
import com.jimbroze.kbus.contracts.messages.event.ErrorStrategy
import com.jimbroze.kbus.contracts.messages.event.EventDestination
import com.jimbroze.kbus.core.module.ContextRuntime
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
 * A context's opt-in durable inbox, declared on the [com.jimbroze.kbus.core.module.BoundedContext]
 * itself. Each declaring context supplies its own [store] instance — structural isolation, so one
 * context's pump cannot see another context's rows. A context that declares none keeps synchronous
 * dispatch.
 *
 * [ackPolicy] is deliberately required rather than defaulted: either default would silently pick a
 * side of a durability trade-off the consumer owns — [InboxAckPolicy.HonourEventStrategy] lets a
 * producer's [ErrorStrategy.FireAndForget] ack a failed handler, and
 * [InboxAckPolicy.RequireHandlerSuccess] retries it indefinitely (there is no attempt cap or
 * dead-letter path yet). Pairing it with [store] here is what makes it unstatable without a durable
 * ack to make stronger.
 */
class ContextInbox(val store: InboxStore, val ackPolicy: InboxAckPolicy)

/**
 * Bus-wide inbox tuning. Which contexts have an inbox, and on what ack policy, is declared per
 * context via [ContextInbox] — this covers only the knobs every pump shares.
 */
class InboxConfig(
    val pollInterval: Duration = 30.seconds,
    val batchSize: Int = 100,
    val opportunisticDispatch: Boolean = true,
)

/**
 * Wraps each [contexts] entry whose [com.jimbroze.kbus.core.module.BoundedContext] declares a
 * [ContextInbox] in an [EventInbox], and leaves the rest untouched. Mirrors
 * [com.jimbroze.kbus.core.uow.OutboxCoordinator]'s shape: config-or-null and a scope in, derived
 * members as `val`s, an idempotent start, and no `stop` — cancellation is the bus's root job.
 *
 * A null [config] means default tuning, not "no inboxes": enablement is the contexts' to declare.
 */
internal class InboxCoordinator(
    config: InboxConfig?,
    contexts: List<ContextRuntime>,
    private val inboxScope: CoroutineScope,
) {
    private val tuning = config ?: InboxConfig()

    val destinations: List<EventDestination> =
        contexts.map { contextRuntime ->
            contextRuntime.context.inbox?.let { inbox ->
                EventInbox(
                    contextRuntime,
                    inbox.store,
                    inboxScope,
                    tuning.batchSize,
                    tuning.pollInterval,
                    tuning.opportunisticDispatch,
                )
            } ?: contextRuntime
        }

    private val inboxes = destinations.filterIsInstance<EventInbox>()

    val isEnabled: Boolean
        get() = inboxes.isNotEmpty()

    private var pumpJobs: List<Job>? = null

    /** Idempotent; a no-op when no inbox is configured. One job per inbox. */
    fun startConsuming() {
        if (pumpJobs != null || inboxes.isEmpty()) return
        pumpJobs = inboxes.map { inbox -> inboxScope.launch { inbox.pump() } }
    }
}
