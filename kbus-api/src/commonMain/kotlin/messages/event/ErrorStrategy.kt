package com.jimbroze.kbus.api.messages.event

sealed interface ErrorStrategy {
    data object FireAndForget : ErrorStrategy

    data object FailFast : ErrorStrategy

    data object ContinueAndAggregate : ErrorStrategy
}
