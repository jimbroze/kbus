package com.jimbroze.kbus.contracts.messages.event

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A stable [id] paired with the [event] to deliver. The id is minted once, at the ingress boundary
 * (see [of]), and must survive every hand-off from publish through routing to delivery: it is what
 * lets an at-least-once consumer (an outbox poller, later an inbox) dedupe a redelivered event.
 */
class EventEnvelope(val id: String, val event: IntegrationEvent) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun of(event: IntegrationEvent): EventEnvelope =
            EventEnvelope(Uuid.random().toString(), event)
    }
}
