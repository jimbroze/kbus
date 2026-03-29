package com.jimbroze.kbus.domain.event

sealed interface DomainEventErrorStrategy {
    data object FireAndForget : DomainEventErrorStrategy

    data object FailFast : DomainEventErrorStrategy

    data object ContinueAndAggregate : DomainEventErrorStrategy
}
