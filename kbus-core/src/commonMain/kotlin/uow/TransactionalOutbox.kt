package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.contracts.outbox.OutboxStore
import com.jimbroze.kbus.core.messages.event.relay.outboxRelay
import com.jimbroze.kbus.core.messages.event.routing.EventRouter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Opt-in configuration for the transactional outbox. A peer of
 * [TransactionManager][com.jimbroze.kbus.contracts.uow.TransactionManager] on the bus constructor,
 * not middleware.
 *
 * [maxConcurrentDeliveries] trades ordering for throughput: see
 * [EnvelopeRelay][com.jimbroze.kbus.core.messages.event.relay.EnvelopeRelay]. Set it to 1 to
 * publish a batch strictly in the order the store returned it.
 */
class OutboxConfig(
    val store: OutboxStore,
    val pollInterval: Duration = 30.seconds,
    val batchSize: Int = 100,
    val opportunisticDrain: Boolean = true,
    val maxConcurrentDeliveries: Int = 16,
)

/**
 * Captures integration events published during a command, deferring the store write until [flush]
 * runs. Self-registers into [unitOfWork] at construction time: [flush] as the *first* secondary
 * work item (so it runs inside the command's transaction, ahead of anything registered while
 * handling the command), and, if [opportunisticDrain] is true, [drain] as post-commit work.
 * Publishes that arrive after [flush] has already run (e.g. from SECONDARY/POST_COMMIT-phase
 * handlers) are saved immediately. [drain] routes the buffered entries through [router] on
 * [drainScope] as a fire-and-forget background task — a latency optimisation only. A bus-owned
 * poller is the at-least-once delivery guarantee; failed or skipped drains are picked up there.
 */
class TransactionalOutbox
internal constructor(
    private val store: OutboxStore,
    private val router: EventRouter,
    private val drainScope: CoroutineScope,
    unitOfWork: UnitOfWork<*>,
    opportunisticDrain: Boolean = true,
    maxConcurrentDeliveries: Int = 16,
) : IntegrationEventPublisher {
    private val mutex = Mutex()
    private val buffer = mutableListOf<EventEnvelope>()
    private val pendingSave = mutableListOf<EventEnvelope>()
    private var flushed = false
    private val relay = outboxRelay(store, router, maxConcurrentDeliveries)

    init {
        unitOfWork.addSecondaryWork { flush() }
        if (opportunisticDrain) unitOfWork.addPostCommitWork { drain() }
    }

    override suspend fun publish(events: List<IntegrationEvent>) {
        val entries = events.map { EventEnvelope.of(it) }

        val alreadyFlushed =
            mutex.withLock {
                buffer.addAll(entries)
                if (!flushed) pendingSave.addAll(entries)
                flushed
            }

        if (alreadyFlushed) store.save(entries)
    }

    /** Saves everything published so far and marks the outbox flushed. Runs as secondary work. */
    private suspend fun flush() {
        val toSave =
            mutex.withLock {
                flushed = true
                pendingSave.toList().also { pendingSave.clear() }
            }

        if (toSave.isNotEmpty()) store.save(toSave)
    }

    private fun drain() {
        drainScope.launch {
            val entries = mutex.withLock { buffer.toList().also { buffer.clear() } }
            relay.relay(entries)
        }
    }
}

/**
 * Outbox-backed publish with no transaction to coordinate with: saves durably first, then delivers
 * opportunistically. Stateless — safe to share as a single long-lived instance. This is the
 * null-[UnitOfWork] counterpart to [TransactionalOutbox]: with nothing to flush on, "publish"
 * collapses to exactly the already-flushed path — save then drain — so it needs no buffer, mutex,
 * or flush bookkeeping.
 */
class ImmediateOutboxPublisher
internal constructor(
    private val store: OutboxStore,
    private val router: EventRouter,
    private val drainScope: CoroutineScope,
    private val opportunisticDrain: Boolean = true,
    maxConcurrentDeliveries: Int = 16,
) : IntegrationEventPublisher {
    private val relay = outboxRelay(store, router, maxConcurrentDeliveries)

    override suspend fun publish(events: List<IntegrationEvent>) {
        val entries = events.map { EventEnvelope.of(it) }

        store.save(entries)
        if (opportunisticDrain) drainScope.launch { relay.relay(entries) }
    }
}

/**
 * Owns everything the outbox needs beyond a single command's scope: the [immediatePublisher] and
 * per-command [create] that publishing goes through, and the bus-wide poller that makes delivery
 * at-least-once. The poller starts on [startPolling], never from a constructor.
 */
class OutboxCoordinator(
    private val config: OutboxConfig?,
    private val router: EventRouter,
    private val outboxScope: CoroutineScope,
) {
    val isEnabled: Boolean
        get() = config != null

    val immediatePublisher: IntegrationEventPublisher? =
        config?.let {
            ImmediateOutboxPublisher(
                it.store,
                router,
                outboxScope,
                it.opportunisticDrain,
                it.maxConcurrentDeliveries,
            )
        }

    private var pollerJob: Job? = null

    fun create(unitOfWork: UnitOfWork<*>): TransactionalOutbox? {
        return config?.let { config ->
            TransactionalOutbox(
                config.store,
                router,
                outboxScope,
                unitOfWork,
                config.opportunisticDrain,
                config.maxConcurrentDeliveries,
            )
        }
    }

    /** Idempotent; a no-op when no outbox is configured. */
    fun startPolling() {
        if (config == null || pollerJob != null) return
        pollerJob =
            outboxScope.launch {
                outboxRelay(config.store, router, config.maxConcurrentDeliveries)
                    .poll(config.batchSize, config.pollInterval)
            }
    }
}
