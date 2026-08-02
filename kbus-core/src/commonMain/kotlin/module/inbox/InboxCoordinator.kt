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

/** When a context acknowledges a consumed event, independent of every other context's choice. */
sealed interface InboxAckPolicy {
    /** Acknowledge as the event's own [ErrorStrategy] dictates. */
    data object HonourEventStrategy : InboxAckPolicy

    /**
     * Acknowledge only once every handler has succeeded, overriding an
     * [ErrorStrategy.FireAndForget] event so its failures are retried rather than swallowed.
     */
    data object RequireHandlerSuccess : InboxAckPolicy
}

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
 * A context's opt-in durable inbox. Each declaring context must supply its own [store] instance: a
 * store shared between contexts breaks their isolation, letting one context consume another's
 * events.
 *
 * [ackPolicy] has no default because neither choice is safe to assume — one can acknowledge an
 * event whose handler failed, the other retries such an event indefinitely, there being no attempt
 * cap or dead-letter path yet.
 */
class ContextInbox(val store: InboxStore, val ackPolicy: InboxAckPolicy)

/**
 * Bus-wide inbox tuning, shared by every context that declares a [ContextInbox].
 *
 * [maxConcurrentDeliveries] trades ordering for throughput: see
 * [EnvelopeRelay][com.jimbroze.kbus.core.messages.event.relay.EnvelopeRelay]. Set it to 1 to
 * consume a batch strictly in the order the store returned it.
 */
class InboxTuning(
    val pollInterval: Duration = 30.seconds,
    val batchSize: Int = 100,
    val opportunisticDispatch: Boolean = true,
    val maxConcurrentDeliveries: Int = 16,
)

/**
 * Gives every context that declares a [ContextInbox] durable, independently acknowledged delivery,
 * and leaves the rest dispatching synchronously.
 *
 * A null [configuredTuning] means default tuning, not "no inboxes" — whether a context has one is
 * the context's own declaration.
 */
internal class InboxCoordinator(
    configuredTuning: InboxTuning?,
    contexts: List<ContextRuntime>,
    private val inboxScope: CoroutineScope,
) {
    private val tuning = configuredTuning ?: InboxTuning()

    val destinations: List<EventDestination> =
        contexts.map { contextRuntime ->
            contextRuntime.context.inbox?.let { inbox ->
                EventInbox(contextRuntime, inbox.store, inboxScope, tuning)
            } ?: contextRuntime
        }

    private val inboxes = destinations.filterIsInstance<EventInbox>()

    val isEnabled: Boolean
        get() = inboxes.isNotEmpty()

    private var pumpJobs: List<Job>? = null

    /** Idempotent; a no-op when no context declared an inbox. */
    fun startConsuming() {
        if (pumpJobs != null || inboxes.isEmpty()) return
        pumpJobs = inboxes.map { inbox -> inboxScope.launch { inbox.pump() } }
    }
}
