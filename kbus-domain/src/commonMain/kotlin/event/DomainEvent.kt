package com.jimbroze.kbus.domain.event

import com.jimbroze.kbus.api.messages.event.Event

abstract class DomainEvent : Event() {
    open val concurrency: Concurrency = Concurrency.Concurrent
    open val errorStrategy: ErrorStrategy = ErrorStrategy.FireAndForget
}
