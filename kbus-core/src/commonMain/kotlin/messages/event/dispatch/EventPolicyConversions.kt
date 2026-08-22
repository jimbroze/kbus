package com.jimbroze.kbus.core.messages.event.dispatch

import com.jimbroze.kbus.api.messages.event.Concurrency as IntegrationConcurrency
import com.jimbroze.kbus.api.messages.event.ErrorStrategy as IntegrationErrorStrategy
import com.jimbroze.kbus.api.messages.event.IntegrationEvent
import com.jimbroze.kbus.domain.event.Concurrency as DomainConcurrency
import com.jimbroze.kbus.domain.event.DispatchTiming as DomainDispatchTiming
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import com.jimbroze.kbus.domain.event.ErrorStrategy as DomainErrorStrategy

internal fun dispatchPhaseFor(handler: DomainEventHandler<*>): DispatchPhase {
    return when (handler.dispatchTiming) {
        DomainDispatchTiming.ImmediatelyInTransaction -> DispatchPhase.IMMEDIATE
        DomainDispatchTiming.AtEndOfTransaction -> DispatchPhase.SECONDARY
        DomainDispatchTiming.AfterTransaction -> DispatchPhase.POST_COMMIT
    }
}

internal fun errorStrategyFor(event: DomainEvent): EventErrorStrategy {
    return when (event.errorStrategy) {
        DomainErrorStrategy.FireAndForget -> EventErrorStrategy.FIRE_AND_FORGET
        DomainErrorStrategy.FailFast -> EventErrorStrategy.FAIL_FAST
        DomainErrorStrategy.ContinueAndAggregate -> EventErrorStrategy.CONTINUE_AND_AGGREGATE
    }
}

internal fun concurrencyFor(event: DomainEvent): EventConcurrency {
    return when (event.concurrency) {
        DomainConcurrency.Concurrent -> EventConcurrency.CONCURRENT
        DomainConcurrency.Sequential -> EventConcurrency.SEQUENTIAL
    }
}

internal fun errorStrategyFor(event: IntegrationEvent): EventErrorStrategy =
    mapErrorStrategy(event.errorStrategy)

/**
 * Converts the public, producer-facing [IntegrationErrorStrategy] into the dispatcher's internal
 * enum.
 */
internal fun mapErrorStrategy(strategy: IntegrationErrorStrategy): EventErrorStrategy {
    return when (strategy) {
        IntegrationErrorStrategy.FireAndForget -> EventErrorStrategy.FIRE_AND_FORGET
        IntegrationErrorStrategy.FailFast -> EventErrorStrategy.FAIL_FAST
        IntegrationErrorStrategy.ContinueAndAggregate -> EventErrorStrategy.CONTINUE_AND_AGGREGATE
    }
}

internal fun concurrencyFor(event: IntegrationEvent): EventConcurrency {
    return when (event.concurrency) {
        IntegrationConcurrency.Concurrent -> EventConcurrency.CONCURRENT
        IntegrationConcurrency.Sequential -> EventConcurrency.SEQUENTIAL
    }
}
