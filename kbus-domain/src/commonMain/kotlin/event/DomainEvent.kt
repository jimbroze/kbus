package com.jimbroze.kbus.domain.event

import com.jimbroze.kbus.contracts.messages.event.Event

abstract class DomainEvent : Event() {
    open val concurrency: DomainEventConcurrency = DomainEventConcurrency.Concurrent
    open val errorStrategy: DomainEventErrorStrategy = DomainEventErrorStrategy.FireAndForget
}
