package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventConcurrency
import com.jimbroze.kbus.domain.event.DomainEventDispatchTiming
import com.jimbroze.kbus.domain.event.DomainEventErrorStrategy
import com.jimbroze.kbus.domain.event.DomainEventHandler

internal fun dispatchPhaseFor(handler: DomainEventHandler<*>): DispatchPhase {
    return when (handler.dispatchTiming) {
        DomainEventDispatchTiming.Immediately -> DispatchPhase.IMMEDIATE
        DomainEventDispatchTiming.AfterPrimaryWork -> DispatchPhase.SECONDARY
        DomainEventDispatchTiming.AfterTransaction -> DispatchPhase.POST_COMMIT
    }
}

internal fun errorStrategyFor(event: DomainEvent): ErrorStrategy {
    return when (event.errorStrategy) {
        DomainEventErrorStrategy.FireAndForget -> ErrorStrategy.FIRE_AND_FORGET
        DomainEventErrorStrategy.FailFast -> ErrorStrategy.FAIL_FAST
        DomainEventErrorStrategy.ContinueAndAggregate -> ErrorStrategy.CONTINUE_AND_AGGREGATE
    }
}

internal fun concurrencyFor(event: DomainEvent): EventConcurrency {
    return when (event.concurrency) {
        DomainEventConcurrency.Concurrent -> EventConcurrency.CONCURRENT
        DomainEventConcurrency.Sequential -> EventConcurrency.SEQUENTIAL
    }
}
