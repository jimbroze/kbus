package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.domain.event.Concurrency
import com.jimbroze.kbus.domain.event.DispatchTiming
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import com.jimbroze.kbus.domain.event.ErrorStrategy

internal fun dispatchPhaseFor(handler: DomainEventHandler<*>): DispatchPhase {
    return when (handler.dispatchTiming) {
        DispatchTiming.Immediately -> DispatchPhase.IMMEDIATE
        DispatchTiming.AfterPrimaryWork -> DispatchPhase.SECONDARY
        DispatchTiming.AfterTransaction -> DispatchPhase.POST_COMMIT
    }
}

internal fun errorStrategyFor(event: DomainEvent): EventErrorStrategy {
    return when (event.errorStrategy) {
        ErrorStrategy.FireAndForget -> EventErrorStrategy.FIRE_AND_FORGET
        ErrorStrategy.FailFast -> EventErrorStrategy.FAIL_FAST
        ErrorStrategy.ContinueAndAggregate -> EventErrorStrategy.CONTINUE_AND_AGGREGATE
    }
}

internal fun concurrencyFor(event: DomainEvent): EventConcurrency {
    return when (event.concurrency) {
        Concurrency.Concurrent -> EventConcurrency.CONCURRENT
        Concurrency.Sequential -> EventConcurrency.SEQUENTIAL
    }
}
