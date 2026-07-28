package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.Concurrency
import com.jimbroze.kbus.contracts.messages.event.ErrorStrategy
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.core.messages.event.dispatch.EventConcurrency
import com.jimbroze.kbus.core.messages.event.dispatch.EventErrorStrategy

internal fun errorStrategyFor(event: IntegrationEvent): EventErrorStrategy =
    mapErrorStrategy(event.errorStrategy)

/** Converts the public, producer-facing [ErrorStrategy] into the dispatcher's internal enum. */
internal fun mapErrorStrategy(strategy: ErrorStrategy): EventErrorStrategy {
    return when (strategy) {
        ErrorStrategy.FireAndForget -> EventErrorStrategy.FIRE_AND_FORGET
        ErrorStrategy.FailFast -> EventErrorStrategy.FAIL_FAST
        ErrorStrategy.ContinueAndAggregate -> EventErrorStrategy.CONTINUE_AND_AGGREGATE
    }
}

internal fun concurrencyFor(event: IntegrationEvent): EventConcurrency {
    return when (event.concurrency) {
        Concurrency.Concurrent -> EventConcurrency.CONCURRENT
        Concurrency.Sequential -> EventConcurrency.SEQUENTIAL
    }
}
