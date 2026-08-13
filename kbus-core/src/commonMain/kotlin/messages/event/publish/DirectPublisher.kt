package com.jimbroze.kbus.core.messages.event.publish

import com.jimbroze.kbus.api.messages.event.ErrorStrategy
import com.jimbroze.kbus.api.messages.event.EventEnvelope
import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import com.jimbroze.kbus.api.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.core.messages.event.routing.EventRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The no-outbox ingress: mints envelopes and routes them, with no durability.
 *
 * A batch is partitioned by each event's own [ErrorStrategy] rather than split per event, so one
 * batch remains one routing attempt. The [ErrorStrategy.FireAndForget] group is launched on
 * [fireAndForgetScope] and not waited on; every other strategy is awaited, so a destination failure
 * reaches the publishing caller.
 *
 * [fireAndForgetScope] must be a scope the bus owns, or the work it launches is beyond the reach of
 * both the stop grace period and cancellation.
 */
class DirectPublisher(
    private val router: EventRouter,
    private val fireAndForgetScope: CoroutineScope,
) : IntegrationEventPublisher {
    override suspend fun publish(events: List<IntegrationEvent>) {
        val (fireAndForget, awaited) =
            events.partition { it.errorStrategy == ErrorStrategy.FireAndForget }

        if (fireAndForget.isNotEmpty()) {
            fireAndForgetScope.launch { router.route(fireAndForget.map { EventEnvelope.of(it) }) }
        }
        if (awaited.isNotEmpty()) {
            router.route(awaited.map { EventEnvelope.of(it) })
        }
    }
}
