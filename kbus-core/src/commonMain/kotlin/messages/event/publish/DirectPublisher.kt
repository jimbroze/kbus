package com.jimbroze.kbus.core.messages.event.publish

import com.jimbroze.kbus.contracts.messages.event.ErrorStrategy
import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.core.messages.event.routing.EventRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The no-outbox ingress: mints envelopes and routes them, with no durability. The only
 * caller-facing integration-publish path (the inbox pump, the outbox poller and the outbox drain
 * are all background coroutines with nobody waiting on them), so it is what preserves today's
 * publish-boundary timing now that dispatch itself always awaits its handlers: a batch is
 * partitioned by each event's own [ErrorStrategy], not split per event (that would multiply
 * [EventRouter]'s observer emissions and turn one batched save into several) — the
 * [ErrorStrategy.FireAndForget] group is launched on [scope] (the producer said it doesn't care, so
 * publish doesn't wait on it), every other strategy is routed and awaited so a destination failure
 * still propagates to the publishing caller.
 */
/**
 * [scope] defaults to a fresh, unparented `Dispatchers.Default` scope for the benefit of throwaway
 * test wiring — [BaseMessageBus][com.jimbroze.kbus.core.bus.BaseMessageBus] always passes its own
 * `eventDispatcherScope` explicitly. A `DirectPublisher` built with the default in a real bus would
 * launch [FireAndForget][ErrorStrategy.FireAndForget] routing that `stop(gracePeriod)` can neither
 * drain nor `rootJob.cancelAndJoin()` cancel — always pass the bus's scope outside of tests.
 */
class DirectPublisher(
    private val router: EventRouter,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : IntegrationEventPublisher {
    override suspend fun publish(events: List<IntegrationEvent>) {
        val (fireAndForget, awaited) =
            events.partition { it.errorStrategy == ErrorStrategy.FireAndForget }

        if (fireAndForget.isNotEmpty()) {
            scope.launch { router.route(fireAndForget.map { EventEnvelope.of(it) }) }
        }
        if (awaited.isNotEmpty()) {
            router.route(awaited.map { EventEnvelope.of(it) })
        }
    }
}
