package com.jimbroze.kbus.contracts.messages.event

sealed interface ErrorStrategy {
    data object FireAndForget : ErrorStrategy

    data object FailFast : ErrorStrategy

    data object ContinueAndAggregate : ErrorStrategy
}
