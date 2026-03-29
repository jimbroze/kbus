package com.jimbroze.kbus.domain.event

sealed interface ErrorStrategy {
    data object FireAndForget : ErrorStrategy

    data object FailFast : ErrorStrategy

    data object ContinueAndAggregate : ErrorStrategy
}
